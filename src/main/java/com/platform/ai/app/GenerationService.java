package com.platform.ai.app;

import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.GenerationRepository;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.generation.ContextChunk;
import com.platform.shared.generation.GenerationPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the constrained generation pipeline: retrieve top-k evidence,
 * cap context to avoid "Lost in the Middle" degradation, call the generation
 * model, persist the candidate answer.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    // Cap and curate context: master-design §10.2. Mid-context evidence is used poorly;
    // 5 chunks is sufficient for step-by-step answers and avoids context overflow.
    static final int MAX_CONTEXT_CHUNKS = 5;

    private final RetrievalService retrievalService;
    private final GenerationPort generationPort;
    private final GenerationRepository generationRepo;

    public GenerationService(RetrievalService retrievalService,
            GenerationPort generationPort,
            GenerationRepository generationRepo) {
        this.retrievalService = retrievalService;
        this.generationPort = generationPort;
        this.generationRepo = generationRepo;
    }

    /**
     * Retrieves evidence, generates a cited candidate answer, and persists it.
     * Non-transactional at this level: retrieval runs in its own read-only transaction
     * (see RetrievalService.retrieve); the persist step is a single atomic INSERT.
     * Idempotent: returns the existing answer if this question was already generated.
     * The answer is pre-verifier; the verifier runs in Slice 14.
     */
    @CircuitBreaker(name = "modelApi", fallbackMethod = "generateFallback")
    @Observed(name = "ai.generate.latency")
    public CandidateAnswer generate(UUID questionId, String questionText) {
        return generationRepo.findByQuestionId(questionId)
                .orElseGet(() -> generateAndPersist(questionId, questionText));
    }

    // Resilience4j fallback: model API unavailable → empty answer → verifier scores
    // near-zero → AiDecisionService routes to ABSTAINED → escalate to human expert queue.
    public CandidateAnswer generateFallback(UUID questionId, String questionText, Throwable ex) {
        log.warn("Model API circuit open for question {}; escalating to human queue. cause={}",
                questionId, ex.getMessage());
        return new CandidateAnswer(List.of());
    }

    public CandidateAnswer generateAndPersist(UUID questionId, String questionText) {
        List<AiCorpusChunk> chunks = retrievalService.retrieve(questionText, MAX_CONTEXT_CHUNKS);

        List<ContextChunk> context = chunks.stream()
                .limit(MAX_CONTEXT_CHUNKS)
                .map(c -> new ContextChunk(c.id(), c.chunkText()))
                .toList();

        CandidateAnswer answer = generationPort.generate(questionText, context);
        generationRepo.save(UUID.randomUUID(), questionId, answer);
        return answer;
    }
}
