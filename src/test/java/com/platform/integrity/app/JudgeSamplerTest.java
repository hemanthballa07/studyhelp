package com.platform.integrity.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.ai.app.JudgeSampler;
import com.platform.shared.generation.AnswerStep;
import com.platform.shared.generation.CandidateAnswer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JudgeSamplerTest {

    @Test
    void alwaysSample_fullyUncited_incrementsHallucinatedCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JudgeSampler sampler = new JudgeSampler(registry, 1.0);
        CandidateAnswer uncited = new CandidateAnswer(
                List.of(new AnswerStep("Some claim with no citation.", List.of())));

        sampler.sampleIfSelected(UUID.randomUUID(), uncited);

        assertThat(registry.counter("ai.hallucination", "result", "hallucinated").count()).isEqualTo(1.0);
        assertThat(registry.counter("ai.hallucination", "result", "clean").count()).isEqualTo(0.0);
    }

    @Test
    void alwaysSample_fullyCited_incrementsCleanCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JudgeSampler sampler = new JudgeSampler(registry, 1.0);
        CandidateAnswer cited = new CandidateAnswer(
                List.of(new AnswerStep("A cited claim.", List.of(UUID.randomUUID()))));

        sampler.sampleIfSelected(UUID.randomUUID(), cited);

        assertThat(registry.counter("ai.hallucination", "result", "clean").count()).isEqualTo(1.0);
        assertThat(registry.counter("ai.hallucination", "result", "hallucinated").count()).isEqualTo(0.0);
    }

    @Test
    void neverSample_noMetricIncrement() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JudgeSampler sampler = new JudgeSampler(registry, 0.0);
        CandidateAnswer uncited = new CandidateAnswer(
                List.of(new AnswerStep("Uncited claim.", List.of())));

        sampler.sampleIfSelected(UUID.randomUUID(), uncited);

        assertThat(registry.counter("ai.hallucination", "result", "hallucinated").count()).isEqualTo(0.0);
        assertThat(registry.counter("ai.hallucination", "result", "clean").count()).isEqualTo(0.0);
    }
}
