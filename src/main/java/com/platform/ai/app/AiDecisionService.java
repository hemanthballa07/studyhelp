package com.platform.ai.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.domain.VerificationResult;
import com.platform.ai.event.AnswerAbstained;
import com.platform.ai.event.AnswerProduced;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies confidence thresholds (§10.5) to a VerificationResult, fans out to all three sinks
 * via VerificationResultMapper, and emits the decision event to the outbox.
 *
 * Confidence: 0.35*groundedness + 0.20*consistency + 0.20*structural + 0.25*math.
 * tau_high >= confidence → PRODUCED; tau_low <= confidence < tau_high → FLAGGED; else ABSTAINED.
 * All weights and thresholds are config-driven (application.yml) so calibration is code-free.
 */
@Service
public class AiDecisionService {

    private final VerificationResultMapper mapper;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final double tauHigh;
    private final double tauLow;
    private final double wGroundedness;
    private final double wConsistency;
    private final double wStructural;
    private final double wMath;

    public AiDecisionService(
            VerificationResultMapper mapper,
            OutboxStore outbox,
            ObjectMapper objectMapper,
            @Value("${platform.ai.confidence.tau-high}") double tauHigh,
            @Value("${platform.ai.confidence.tau-low}") double tauLow,
            @Value("${platform.ai.confidence.weights.groundedness}") double wGroundedness,
            @Value("${platform.ai.confidence.weights.consistency}") double wConsistency,
            @Value("${platform.ai.confidence.weights.structural}") double wStructural,
            @Value("${platform.ai.confidence.weights.domain-verifier}") double wMath) {
        this.mapper = mapper;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.tauHigh = tauHigh;
        this.tauLow = tauLow;
        this.wGroundedness = wGroundedness;
        this.wConsistency = wConsistency;
        this.wStructural = wStructural;
        this.wMath = wMath;
    }

    @Transactional
    public DecisionOutcome decide(UUID questionId, VerificationResult result, String subject) {
        double confidence = wGroundedness * result.groundednessScore()
                + wConsistency * result.consistencyScore()
                + wStructural * result.structuralScore()
                + wMath * result.mathScore();

        DecisionOutcome outcome;
        if (confidence >= tauHigh) {
            outcome = DecisionOutcome.PRODUCED;
        } else if (confidence >= tauLow) {
            outcome = DecisionOutcome.FLAGGED;
        } else {
            outcome = DecisionOutcome.ABSTAINED;
        }

        mapper.map(result, outcome, confidence);
        emitDecisionEvent(questionId, confidence, outcome, subject);
        return outcome;
    }

    private void emitDecisionEvent(UUID questionId, double confidence,
            DecisionOutcome outcome, String subject) {
        if (outcome == DecisionOutcome.ABSTAINED) {
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), questionId, "Verification",
                    AnswerAbstained.TYPE,
                    toJson(new AnswerAbstained(questionId, confidence, subject)),
                    Instant.now()));
        } else {
            boolean flagged = (outcome == DecisionOutcome.FLAGGED);
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), questionId, "Verification",
                    AnswerProduced.TYPE,
                    toJson(new AnswerProduced(questionId, confidence, flagged)),
                    Instant.now()));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise decision event", ex);
        }
    }
}
