package com.platform.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.platform.ai.domain.GenerationRepository;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.generation.GenerationPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Resilience4j fallback returns an empty CandidateAnswer that drives
 * AiDecisionService to ABSTAINED (escalate to human expert queue) when the model API is down.
 */
class CircuitBreakerFallbackTest {

    private final GenerationService svc = new GenerationService(
            mock(RetrievalService.class),
            mock(GenerationPort.class),
            mock(GenerationRepository.class));

    @Test
    void generateFallback_returnsEmptyCandidateAnswer() {
        CandidateAnswer result = svc.generateFallback(
                UUID.randomUUID(), "any question text", new RuntimeException("model down"));

        assertThat(result.steps()).isEmpty();
        assertThat(result.isFullyCited()).isFalse();
        assertThat(result.citationCoverage()).isEqualTo(0.0);
    }
}
