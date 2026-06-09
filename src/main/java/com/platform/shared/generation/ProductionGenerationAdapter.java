package com.platform.shared.generation;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Production placeholder — returns a single uncited step with a not-configured message
 * so {@code bootRun} does not crash when an LLM integration has not been wired up.
 * Replace with a Spring AI / OpenAI adapter in the production deployment.
 */
@Service
@Profile("!test")
public class ProductionGenerationAdapter implements GenerationPort {

    private static final Logger log = LoggerFactory.getLogger(ProductionGenerationAdapter.class);

    @Override
    public CandidateAnswer generate(String question, List<ContextChunk> context) {
        log.warn("GenerationPort not configured; returning empty answer. " +
                 "Wire a real LLM adapter to enable constrained generation.");
        return new CandidateAnswer(List.of(
                new AnswerStep("Generation model not configured.", List.of())));
    }
}
