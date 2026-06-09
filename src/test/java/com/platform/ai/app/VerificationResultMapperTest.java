package com.platform.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.ai.domain.VerificationRepository;
import com.platform.ai.domain.VerificationResult;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationResultMapperTest {

    private final Tracer tracer = mock(Tracer.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final VerificationRepository verificationRepo = mock(VerificationRepository.class);

    private final VerificationResultMapper mapper =
            new VerificationResultMapper(tracer, registry, verificationRepo);

    @Test
    void map_recordsConfidenceInPrometheusWithDecisionTag() {
        UUID qId = UUID.randomUUID();
        VerificationResult result = new VerificationResult(UUID.randomUUID(), qId, 0.9, 0.8, 0.85, 1.0, 0.8875);
        when(verificationRepo.findByQuestionId(qId)).thenReturn(Optional.of(result));

        mapper.map(result, DecisionOutcome.PRODUCED, 0.91);

        DistributionSummary summary = registry.find("ai.verification.confidence")
                .tag("decision", "PRODUCED")
                .summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isCloseTo(0.91, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void map_setsOtelSpanTagsWhenSpanActive() {
        UUID qId = UUID.randomUUID();
        VerificationResult result = new VerificationResult(UUID.randomUUID(), qId, 0.8, 0.7, 0.75, 0.5, 0.6875);
        Span span = mock(Span.class);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(tracer.currentSpan()).thenReturn(span);
        when(verificationRepo.findByQuestionId(qId)).thenReturn(Optional.of(result));

        mapper.map(result, DecisionOutcome.FLAGGED, 0.70);

        org.mockito.Mockito.verify(span).tag("ai.decision", "FLAGGED");
        org.mockito.Mockito.verify(span).tag("ai.confidence", "0.7");
    }

    @Test
    void map_gracefulWhenNoActiveSpan() {
        UUID qId = UUID.randomUUID();
        VerificationResult result = new VerificationResult(UUID.randomUUID(), qId, 0.3, 0.5, 0.4, 0.0, 0.3);
        when(tracer.currentSpan()).thenReturn(null);
        when(verificationRepo.findByQuestionId(qId)).thenReturn(Optional.of(result));

        // should not throw even with no active span
        mapper.map(result, DecisionOutcome.ABSTAINED, 0.32);
    }
}
