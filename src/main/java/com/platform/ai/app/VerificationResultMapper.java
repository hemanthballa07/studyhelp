package com.platform.ai.app;

import com.platform.ai.domain.VerificationRepository;
import com.platform.ai.domain.VerificationResult;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Single fan-out point for a VerificationResult once the decision is made (§10.6). Writes to all
 * three sinks: OTel span attributes, Prometheus DistributionSummary, and a DB presence assertion.
 * Adding a fourth sink means touching this class only.
 */
@Component
public class VerificationResultMapper {

    private static final Logger log = LoggerFactory.getLogger(VerificationResultMapper.class);

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final VerificationRepository verificationRepo;

    public VerificationResultMapper(Tracer tracer, MeterRegistry meterRegistry,
            VerificationRepository verificationRepo) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.verificationRepo = verificationRepo;
    }

    public void map(VerificationResult result, DecisionOutcome decision, double confidence) {
        otelSink(result, decision, confidence);
        prometheusSink(decision, confidence);
        dbSink(result);
    }

    private void otelSink(VerificationResult result, DecisionOutcome decision, double confidence) {
        var span = tracer.currentSpan();
        if (span == null) return;
        span.tag("ai.groundedness", String.valueOf(result.groundednessScore()))
            .tag("ai.structural", String.valueOf(result.structuralScore()))
            .tag("ai.consistency", String.valueOf(result.consistencyScore()))
            .tag("ai.math", String.valueOf(result.mathScore()))
            .tag("ai.confidence", String.valueOf(confidence))
            .tag("ai.decision", decision.name());
    }

    private void prometheusSink(DecisionOutcome decision, double confidence) {
        DistributionSummary.builder("ai.verification.confidence")
                .tag("decision", decision.name())
                .register(meterRegistry)
                .record(confidence);
    }

    private void dbSink(VerificationResult result) {
        if (verificationRepo.findByQuestionId(result.questionId()).isEmpty()) {
            log.warn("VerificationResult for question {} not found in DB at mapper time; "
                    + "VerificationService should have persisted it first", result.questionId());
        }
    }
}
