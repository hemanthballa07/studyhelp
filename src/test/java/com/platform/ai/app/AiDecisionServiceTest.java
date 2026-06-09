package com.platform.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.domain.VerificationResult;
import com.platform.ai.event.AnswerAbstained;
import com.platform.ai.event.AnswerProduced;
import com.platform.shared.outbox.OutboxStore;
import com.platform.shared.telemetry.PipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Adversarial test for Slice 15 decision thresholds — written FIRST (RED) before AiDecisionService
 * is implemented. Tests boundary conditions and a gold set calibration.
 *
 * Gold set target (§10.5): deliver-rate (PRODUCED + FLAGGED) >= 70% on the 6-example fixture;
 * no false PRODUCED for groundedness < 0.4.
 * Weights: g=0.35, c=0.20, s=0.20, m=0.25; tau_high=0.82, tau_low=0.65.
 */
class AiDecisionServiceTest {

    private static final double TAU_HIGH = 0.82;
    private static final double TAU_LOW  = 0.65;
    private static final double W_G = 0.35;
    private static final double W_C = 0.20;
    private static final double W_S = 0.20;
    private static final double W_M = 0.25;

    private final VerificationResultMapper mapper = mock(VerificationResultMapper.class);
    private final OutboxStore outbox = mock(OutboxStore.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiDecisionService svc;

    @BeforeEach
    void setUp() {
        svc = new AiDecisionService(mapper, outbox, objectMapper,
                new PipelineMetrics(new SimpleMeterRegistry()),
                TAU_HIGH, TAU_LOW, W_G, W_C, W_S, W_M);
    }

    // ── Boundary tests ────────────────────────────────────────────────────────

    @Test
    void aboveHighThreshold_emitsAnswerProduced_notFlagged() {
        // confidence = 0.35*0.95 + 0.20*0.90 + 0.20*0.85 + 0.25*1.0 = 0.9325
        UUID qId = UUID.randomUUID();
        VerificationResult result = result(qId, 0.95, 0.85, 0.90, 1.0);

        DecisionOutcome outcome = svc.decide(qId, result, "PHYSICS");

        assertThat(outcome).isEqualTo(DecisionOutcome.PRODUCED);
        verify(outbox).append(argThat(e ->
                AnswerProduced.TYPE.equals(e.eventType())
                && e.payload().contains("\"flagged\":false")));
    }

    @Test
    void betweenThresholds_emitsAnswerProduced_flagged() {
        // confidence = 0.35*0.80 + 0.20*0.70 + 0.20*0.75 + 0.25*0.5 = 0.695
        UUID qId = UUID.randomUUID();
        VerificationResult result = result(qId, 0.80, 0.75, 0.70, 0.5);

        DecisionOutcome outcome = svc.decide(qId, result, "MATH");

        assertThat(outcome).isEqualTo(DecisionOutcome.FLAGGED);
        verify(outbox).append(argThat(e ->
                AnswerProduced.TYPE.equals(e.eventType())
                && e.payload().contains("\"flagged\":true")));
    }

    @Test
    void belowLowThreshold_emitsAnswerAbstained() {
        // confidence = 0.35*0.30 + 0.20*0.40 + 0.20*0.50 + 0.25*0.0 = 0.285
        UUID qId = UUID.randomUUID();
        VerificationResult result = result(qId, 0.30, 0.50, 0.40, 0.0);

        DecisionOutcome outcome = svc.decide(qId, result, "CHEMISTRY");

        assertThat(outcome).isEqualTo(DecisionOutcome.ABSTAINED);
        verify(outbox).append(argThat(e -> AnswerAbstained.TYPE.equals(e.eventType())));
    }

    @Test
    void exactlyAtHighThreshold_isProducedNotFlagged() {
        // confidence = 0.82*1.0 = 0.82 (all signals equal tau_high, weights sum to 1)
        UUID qId = UUID.randomUUID();
        VerificationResult result = result(qId, 0.82, 0.82, 0.82, 0.82);

        DecisionOutcome outcome = svc.decide(qId, result, "PHYSICS");

        assertThat(outcome).isEqualTo(DecisionOutcome.PRODUCED);
        verify(outbox).append(argThat(e ->
                AnswerProduced.TYPE.equals(e.eventType())
                && e.payload().contains("\"flagged\":false")));
    }

    @Test
    void exactlyAtLowThreshold_isFlaggedNotAbstained() {
        // confidence = 0.65*1.0 = 0.65 (all signals equal tau_low, weights sum to 1)
        UUID qId = UUID.randomUUID();
        VerificationResult result = result(qId, 0.65, 0.65, 0.65, 0.65);

        DecisionOutcome outcome = svc.decide(qId, result, "MATH");

        assertThat(outcome).isEqualTo(DecisionOutcome.FLAGGED);
        verify(outbox).append(argThat(e ->
                AnswerProduced.TYPE.equals(e.eventType())
                && e.payload().contains("\"flagged\":true")));
    }

    // ── Gold set calibration ──────────────────────────────────────────────────

    /**
     * Mini gold set: 6 examples with known signal values and expected outcomes.
     * Confidence formula: 0.35*g + 0.20*c + 0.20*s + 0.25*m.
     * Stated target: deliver-rate (PRODUCED + FLAGGED) >= 70%; no false PRODUCED for g < 0.4.
     */
    @Test
    void goldSet_deliverRateMeetsStatedTarget() {
        record Example(double g, double s, double c, double m, DecisionOutcome expected) {}

        Example[] gold = {
            // confident → PRODUCED  (conf ≈ 0.9325)
            new Example(0.95, 0.85, 0.90, 1.00, DecisionOutcome.PRODUCED),
            // confident → PRODUCED  (conf ≈ 0.895)
            new Example(0.90, 0.80, 0.85, 1.00, DecisionOutcome.PRODUCED),
            // borderline → FLAGGED  (conf ≈ 0.695)
            new Example(0.80, 0.75, 0.70, 0.50, DecisionOutcome.FLAGGED),
            // borderline → FLAGGED  (conf ≈ 0.658)
            new Example(0.75, 0.70, 0.65, 0.50, DecisionOutcome.FLAGGED),
            // borderline → FLAGGED  (conf ≈ 0.808)
            new Example(0.85, 0.80, 0.75, 0.80, DecisionOutcome.FLAGGED),
            // low quality → ABSTAINED  (conf ≈ 0.285)
            new Example(0.30, 0.50, 0.40, 0.00, DecisionOutcome.ABSTAINED),
        };

        int delivered = 0;
        boolean falseProdWithLowGroundedness = false;
        for (Example ex : gold) {
            UUID qId = UUID.randomUUID();
            VerificationResult vr = result(qId, ex.g(), ex.s(), ex.c(), ex.m());
            DecisionOutcome actual = svc.decide(qId, vr, "SUBJECT");
            assertThat(actual)
                    .as("g=%.2f s=%.2f c=%.2f m=%.2f expected=%s",
                            ex.g(), ex.s(), ex.c(), ex.m(), ex.expected())
                    .isEqualTo(ex.expected());
            if (actual != DecisionOutcome.ABSTAINED) delivered++;
            if (ex.g() < 0.4 && actual == DecisionOutcome.PRODUCED) falseProdWithLowGroundedness = true;
        }
        double deliverRate = (double) delivered / gold.length;
        assertThat(deliverRate).as("deliver-rate must be >= 70%%").isGreaterThanOrEqualTo(0.70);
        assertThat(falseProdWithLowGroundedness).as("no false PRODUCED for groundedness < 0.4").isFalse();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Constructs a VerificationResult in field order: (id, qId, groundedness, structural, consistency, math, aggregate). */
    private static VerificationResult result(UUID qId, double g, double s, double c, double m) {
        double aggregate = (g + s + c + m) / 4.0;
        return new VerificationResult(UUID.randomUUID(), qId, g, s, c, m, aggregate);
    }
}
