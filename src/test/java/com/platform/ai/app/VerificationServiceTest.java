package com.platform.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.VerificationRepository;
import com.platform.ai.domain.VerificationResult;
import com.platform.shared.generation.AnswerStep;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.generation.GenerationPort;
import com.platform.shared.math.MathVerifierPort;
import com.platform.shared.outbox.OutboxStore;
import com.platform.shared.qc.StructuralQcPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificationServiceTest {

    private static final UUID CHUNK_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID CHUNK_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private final StructuralQcPort structuralQcPort = mock(StructuralQcPort.class);
    private final MathVerifierPort mathVerifierPort = mock(MathVerifierPort.class);
    private final GenerationPort generationPort = mock(GenerationPort.class);
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final VerificationRepository verificationRepo = mock(VerificationRepository.class);
    private final OutboxStore outbox = mock(OutboxStore.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private VerificationService svc;

    @BeforeEach
    void setUp() {
        svc = new VerificationService(structuralQcPort, mathVerifierPort, generationPort,
                retrievalService, verificationRepo, outbox, objectMapper);
    }

    // ── Groundedness signal ────────────────────────────────────────────────────

    @Test
    void groundedness_fullyCitedWithLexicalOverlap_scoresHigh() {
        AiCorpusChunk chunk = new AiCorpusChunk(CHUNK_A, "src", "lic",
                "Newton second law force mass acceleration");
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("Newton law relates force mass acceleration", List.of(CHUNK_A))));

        double score = svc.computeGroundedness(candidate, List.of(chunk));

        // citationCoverage = 1.0, lexicalOverlap = 1.0 → 0.5*1 + 0.5*1 = 1.0
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void groundedness_uncitedSteps_reducesScore() {
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("step with no citation", List.of())));

        double score = svc.computeGroundedness(candidate, List.of());

        // citationCoverage = 0.0, lexicalOverlap = 0.0 → 0.0
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void groundedness_citedButNoLexicalOverlap_scoresHalf() {
        AiCorpusChunk chunk = new AiCorpusChunk(CHUNK_A, "src", "lic", "completely unrelated text");
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("force mass acceleration", List.of(CHUNK_A))));

        double score = svc.computeGroundedness(candidate, List.of(chunk));

        // citationCoverage = 1.0, lexicalOverlap = 0.0 → 0.5
        assertThat(score).isEqualTo(0.5);
    }

    // ── Adaptive self-consistency signal ──────────────────────────────────────

    @Test
    void consistency_notBorderline_callsGenerationPortOnce() {
        // citationCoverage = 1.0 (above threshold) → N=1
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("final answer 42", List.of(CHUNK_A))));
        CandidateAnswer sample = new CandidateAnswer(List.of(
                new AnswerStep("final answer 42", List.of(CHUNK_A))));
        when(generationPort.generate(anyString(), any())).thenReturn(sample);

        double score = svc.computeConsistency("what is 6*7", candidate, List.of());

        verify(generationPort, times(1)).generate(anyString(), any());
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void consistency_borderline_callsGenerationPortEscalatedTimes() {
        // citationCoverage = 0.0 (below threshold) → N=3
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("final answer 42", List.of())));
        CandidateAnswer matchingSample = new CandidateAnswer(List.of(
                new AnswerStep("final answer 42", List.of(CHUNK_A))));
        when(generationPort.generate(anyString(), any())).thenReturn(matchingSample);

        double score = svc.computeConsistency("question", candidate, List.of());

        verify(generationPort, times(VerificationService.ESCALATED_SAMPLES)).generate(anyString(), any());
        assertThat(score).isEqualTo(1.0); // all 3 samples match
    }

    @Test
    void consistency_disagreement_returnsPartialScore() {
        // citationCoverage = 0.0 → N=3; 1 of 3 samples match
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("final answer 42", List.of())));
        CandidateAnswer matchSample = new CandidateAnswer(List.of(
                new AnswerStep("final answer 42", List.of())));
        CandidateAnswer mismatchSample = new CandidateAnswer(List.of(
                new AnswerStep("different answer", List.of())));
        when(generationPort.generate(anyString(), any()))
                .thenReturn(matchSample)
                .thenReturn(mismatchSample)
                .thenReturn(mismatchSample);

        double score = svc.computeConsistency("question", candidate, List.of());

        assertThat(score).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(0.001));
    }

    // ── Full verify flow ──────────────────────────────────────────────────────

    @Test
    void verify_computesAllSignalsAndPersists() {
        UUID questionId = UUID.randomUUID();
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("answer step one", List.of(CHUNK_A)),
                new AnswerStep("final answer", List.of(CHUNK_B))));

        when(verificationRepo.findByQuestionId(questionId)).thenReturn(Optional.empty());
        when(verificationRepo.save(any())).thenReturn(1);
        when(retrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of(
                new AiCorpusChunk(CHUNK_A, "src", "lic", "answer step related content"),
                new AiCorpusChunk(CHUNK_B, "src", "lic", "final related content")));
        when(structuralQcPort.score(anyString(), anyString())).thenReturn(0.75);
        when(mathVerifierPort.verify(anyString(), anyString())).thenReturn(0.5);
        when(generationPort.generate(anyString(), any())).thenReturn(candidate);

        VerificationResult result = svc.verify(questionId, "question text", "PHYSICS", candidate);

        assertThat(result.questionId()).isEqualTo(questionId);
        assertThat(result.groundednessScore()).isBetween(0.0, 1.0);
        assertThat(result.structuralScore()).isEqualTo(0.75);
        assertThat(result.consistencyScore()).isBetween(0.0, 1.0);
        assertThat(result.mathScore()).isEqualTo(0.5);
        assertThat(result.aggregateScore()).isBetween(0.0, 1.0);
        verify(verificationRepo).save(any(VerificationResult.class));
        verify(outbox).append(any());
    }

    @Test
    void verify_idempotent_doesNotReRunSignals() {
        UUID questionId = UUID.randomUUID();
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("cached answer", List.of())));
        VerificationResult cached = new VerificationResult(
                UUID.randomUUID(), questionId, 0.8, 0.7, 1.0, 0.5, 0.75);
        when(verificationRepo.findByQuestionId(questionId)).thenReturn(Optional.of(cached));

        VerificationResult result = svc.verify(questionId, "q", "MATH", candidate);

        assertThat(result).isEqualTo(cached);
        verify(verificationRepo, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void aggregateScore_isMeanOfFourSignals() {
        UUID questionId = UUID.randomUUID();
        CandidateAnswer candidate = new CandidateAnswer(List.of(
                new AnswerStep("step", List.of(CHUNK_A))));

        when(verificationRepo.findByQuestionId(questionId)).thenReturn(Optional.empty());
        when(verificationRepo.save(any())).thenReturn(1);
        when(retrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of(
                new AiCorpusChunk(CHUNK_A, "src", "lic", "step related")));
        when(structuralQcPort.score(anyString(), anyString())).thenReturn(0.8);
        when(mathVerifierPort.verify(anyString(), anyString())).thenReturn(0.5);
        when(generationPort.generate(anyString(), any())).thenReturn(candidate);

        VerificationResult result = svc.verify(questionId, "q", "PHYSICS", candidate);

        // aggregate must equal mean of the 4 component scores
        double expected = (result.groundednessScore() + result.structuralScore()
                + result.consistencyScore() + result.mathScore()) / 4.0;
        assertThat(result.aggregateScore()).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
    }
}
