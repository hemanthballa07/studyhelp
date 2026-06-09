package com.platform.ai.app;

import com.platform.ai.domain.AiCorpusChunk;
import com.platform.ai.domain.GenerationRepository;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.generation.ContextChunk;
import com.platform.shared.generation.GenerationPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the constrained generation pipeline: retrieve top-k evidence,
 * cap context to avoid "Lost in the Middle" degradation, call the generation
 * model, persist the candidate answer.
 */
@Service
public class GenerationService {

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
     * The answer is pre-verifier; the verifier runs in Slice 14.
     */
    @Transactional
    public CandidateAnswer generate(UUID questionId, String questionText) {
        List<AiCorpusChunk> chunks = retrievalService.retrieve(questionText, MAX_CONTEXT_CHUNKS);

        List<ContextChunk> context = chunks.stream()
                .map(c -> new ContextChunk(c.id(), c.chunkText()))
                .toList();

        CandidateAnswer answer = generationPort.generate(questionText, context);

        generationRepo.save(UUID.randomUUID(), questionId, answer);
        return answer;
    }
}
