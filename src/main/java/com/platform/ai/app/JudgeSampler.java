package com.platform.ai.app;

import com.platform.shared.generation.CandidateAnswer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Samples X% of delivered answers with a structural hallucination judge (§11).
 * Emits ai.hallucination{result=hallucinated|clean} — data source for HallucinationRateExceedsSLO.
 * Heuristic: citation coverage. Swap in a real LLM judge in a later slice.
 */
@Component
public class JudgeSampler {

    private final double sampleRate;
    private final Counter hallucinatedCounter;
    private final Counter cleanCounter;

    public JudgeSampler(MeterRegistry registry,
            @Value("${platform.integrity.judge.sample-rate}") double sampleRate) {
        this.sampleRate = sampleRate;
        this.hallucinatedCounter = Counter.builder("ai.hallucination")
                .tag("result", "hallucinated")
                .description("Judge-sampled answers flagged as potential hallucinations")
                .register(registry);
        this.cleanCounter = Counter.builder("ai.hallucination")
                .tag("result", "clean")
                .description("Judge-sampled answers passing the hallucination check")
                .register(registry);
    }

    public void sampleIfSelected(UUID questionId, CandidateAnswer answer) {
        if (ThreadLocalRandom.current().nextDouble() >= sampleRate) {
            return;
        }
        if (answer.isFullyCited()) {
            cleanCounter.increment();
        } else {
            hallucinatedCounter.increment();
        }
    }
}
