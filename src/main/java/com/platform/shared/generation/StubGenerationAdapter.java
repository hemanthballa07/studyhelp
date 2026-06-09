package com.platform.shared.generation;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Test-profile stub: returns a deterministic two-step answer where each step cites
 * the first available chunk. Used in all integration tests so generation is fast,
 * offline, and produces structurally valid citation coverage.
 */
@Service
@Profile("test")
public class StubGenerationAdapter implements GenerationPort {

    @Override
    public CandidateAnswer generate(String question, List<ContextChunk> context) {
        if (context.isEmpty()) {
            return new CandidateAnswer(List.of(
                    new AnswerStep("No supporting evidence found in corpus.", List.of())));
        }
        ContextChunk primary = context.get(0);
        List<ContextChunk> secondary = context.size() > 1 ? context.subList(1, 2) : List.of();

        AnswerStep step1 = new AnswerStep(
                "Based on the evidence: " + primary.text(),
                List.of(primary.id()));

        if (secondary.isEmpty()) {
            return new CandidateAnswer(List.of(step1));
        }

        AnswerStep step2 = new AnswerStep(
                "Additionally: " + secondary.get(0).text(),
                List.of(secondary.get(0).id()));

        return new CandidateAnswer(List.of(step1, step2));
    }
}
