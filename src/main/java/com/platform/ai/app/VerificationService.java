package com.platform.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.VerificationRepository;
import com.platform.ai.domain.VerificationResult;
import com.platform.ai.event.VerificationCompleted;
import com.platform.shared.generation.AnswerStep;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.generation.ContextChunk;
import com.platform.shared.generation.GenerationPort;
import com.platform.shared.code.CodeVerifierPort;
import com.platform.shared.math.MathVerifierPort;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import com.platform.shared.qc.StructuralQcPort;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the 4-signal verifier (§10.3–10.4):
 *  1. Groundedness — citation coverage + lexical overlap of step text with cited chunks.
 *  2. Structural QC — reuses QcRubricScorer via StructuralQcPort (ArchUnit-safe; no qc import).
 *  3. Adaptive self-consistency — N=1 default; escalate to 3 if borderline citation coverage.
 *  4. Domain — code answers: LLM judge via CodeVerifierPort; math answers: SymPy via MathVerifierPort.
 *
 * Self-consistency calls GenerationPort directly (not GenerationService) to avoid hitting the
 * idempotency guard that would return the cached answer for every repeated call.
 */
@Service
public class VerificationService {

    // Coverage below this threshold triggers escalated self-consistency sampling.
    static final double BORDERLINE_COVERAGE_THRESHOLD = 0.8;
    static final int ESCALATED_SAMPLES = 3;

    private final StructuralQcPort structuralQcPort;
    private final MathVerifierPort mathVerifierPort;
    private final CodeVerifierPort codeVerifierPort;
    private final GenerationPort generationPort;
    private final RetrievalService retrievalService;
    private final VerificationRepository verificationRepo;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;

    public VerificationService(
            StructuralQcPort structuralQcPort,
            MathVerifierPort mathVerifierPort,
            CodeVerifierPort codeVerifierPort,
            GenerationPort generationPort,
            RetrievalService retrievalService,
            VerificationRepository verificationRepo,
            OutboxStore outbox,
            ObjectMapper objectMapper) {
        this.structuralQcPort = structuralQcPort;
        this.mathVerifierPort = mathVerifierPort;
        this.codeVerifierPort = codeVerifierPort;
        this.generationPort = generationPort;
        this.retrievalService = retrievalService;
        this.verificationRepo = verificationRepo;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs all 4 signals, persists the result, and emits {@code VerificationCompleted}.
     * Idempotent: returns the existing result if this question was already verified.
     */
    @Observed(name = "ai.verify.latency")
    @Transactional
    public VerificationResult verify(UUID questionId, String questionText, String subjectCode,
            CandidateAnswer candidate) {
        return verificationRepo.findByQuestionId(questionId)
                .orElseGet(() -> runVerification(questionId, questionText, subjectCode, candidate));
    }

    private VerificationResult runVerification(UUID questionId, String questionText,
            String subjectCode, CandidateAnswer candidate) {
        List<AiCorpusChunk> chunks = retrievalService.retrieve(questionText, GenerationService.MAX_CONTEXT_CHUNKS);

        double groundedness = computeGroundedness(candidate, chunks);
        double structural = structuralQcPort.score(toBodyText(candidate), subjectCode);
        double consistency = computeConsistency(questionText, candidate, chunks);
        double math = hasCodeBlock(candidate)
                ? codeVerifierPort.verify(questionText, extractCodeBlock(candidate))
                : mathVerifierPort.verify(questionText, extractFinalAnswer(candidate));
        double aggregate = (groundedness + structural + consistency + math) / 4.0;

        VerificationResult result = new VerificationResult(
                UUID.randomUUID(), questionId, groundedness, structural, consistency, math, aggregate);
        int inserted = verificationRepo.save(result);

        // Only emit the outbox event when this transaction actually wrote the row.
        // Guards against duplicate events under concurrent callers that both passed the
        // idempotency check before either transaction committed.
        if (inserted > 0) {
            VerificationCompleted event = new VerificationCompleted(questionId, aggregate);
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), questionId, "Verification",
                    VerificationCompleted.TYPE, toJson(event), Instant.now()));
        }

        return result;
    }

    // ── Signal 1: Groundedness ────────────────────────────────────────────────

    double computeGroundedness(CandidateAnswer candidate, List<AiCorpusChunk> chunks) {
        double citationCov = candidate.citationCoverage();
        double lexical = computeLexicalOverlap(candidate, chunks);
        return 0.5 * citationCov + 0.5 * lexical;
    }

    private double computeLexicalOverlap(CandidateAnswer candidate, List<AiCorpusChunk> chunks) {
        if (candidate.steps().isEmpty()) return 0.0;
        Map<UUID, String> chunkById = chunks.stream()
                .collect(Collectors.toMap(AiCorpusChunk::id, AiCorpusChunk::chunkText));

        long overlapping = candidate.steps().stream()
                .filter(step -> !step.citationChunkIds().isEmpty())
                .filter(step -> step.citationChunkIds().stream()
                        .map(id -> chunkById.getOrDefault(id, ""))
                        .anyMatch(chunkText -> hasLexicalOverlap(step.text(), chunkText)))
                .count();

        return (double) overlapping / candidate.steps().size();
    }

    private boolean hasLexicalOverlap(String stepText, String chunkText) {
        Set<String> stepTokens = tokenize(stepText);
        Set<String> chunkTokens = tokenize(chunkText);
        stepTokens.retainAll(chunkTokens);
        return !stepTokens.isEmpty();
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>(Arrays.asList(
                text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+")));
        tokens.remove(""); // split on blank/punctuation-only input can produce ""
        return tokens;
    }

    // ── Signal 3: Adaptive self-consistency ──────────────────────────────────

    double computeConsistency(String questionText, CandidateAnswer candidate,
            List<AiCorpusChunk> chunks) {
        List<ContextChunk> context = toContextChunks(chunks);
        String candidateFinal = extractFinalAnswer(candidate);
        boolean borderline = candidate.citationCoverage() < BORDERLINE_COVERAGE_THRESHOLD;
        int n = borderline ? ESCALATED_SAMPLES : 1;

        long agreements = 0;
        for (int i = 0; i < n; i++) {
            CandidateAnswer sample = generationPort.generate(questionText, context);
            if (normalizedMatch(candidateFinal, extractFinalAnswer(sample))) {
                agreements++;
            }
        }
        return (double) agreements / n;
    }

    private boolean normalizedMatch(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private String normalize(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]", "").strip();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractFinalAnswer(CandidateAnswer candidate) {
        List<AnswerStep> steps = candidate.steps();
        return steps.isEmpty() ? "" : steps.get(steps.size() - 1).text();
    }

    // Returns true if any step text contains a markdown code fence or Python REPL prefix.
    boolean hasCodeBlock(CandidateAnswer candidate) {
        return candidate.steps().stream()
                .anyMatch(s -> s.text().contains("```") || s.text().contains(">>> "));
    }

    // Extracts content between the first pair of ``` fences; falls back to last step text.
    String extractCodeBlock(CandidateAnswer candidate) {
        for (AnswerStep step : candidate.steps()) {
            String text = step.text();
            int open = text.indexOf("```");
            if (open >= 0) {
                // skip the language tag on the opening fence line if present
                int contentStart = text.indexOf('\n', open);
                if (contentStart < 0) contentStart = open + 3;
                else contentStart++;
                int close = text.indexOf("```", contentStart);
                if (close > contentStart) {
                    return text.substring(contentStart, close).strip();
                }
            }
        }
        return extractFinalAnswer(candidate);
    }

    private String toBodyText(CandidateAnswer candidate) {
        return candidate.steps().stream()
                .map(AnswerStep::text)
                .collect(Collectors.joining("\n"));
    }

    private List<ContextChunk> toContextChunks(List<AiCorpusChunk> chunks) {
        return chunks.stream()
                .map(c -> new ContextChunk(c.id(), c.chunkText()))
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise event", e);
        }
    }
}
