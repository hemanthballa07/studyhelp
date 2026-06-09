package com.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.ai.app.AiDecisionService;
import com.platform.ai.app.CorpusIngestionService;
import com.platform.ai.app.DecisionOutcome;
import com.platform.ai.app.GenerationService;
import com.platform.ai.app.VerificationService;
import com.platform.ai.domain.VerificationResult;
import com.platform.ai.event.AnswerAbstained;
import com.platform.ai.event.AnswerProduced;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.support.PostgresContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Integration test for AiDecisionService on a real Postgres container:
 * 1. High-confidence result emits AnswerProduced to the outbox.
 * 2. Low-confidence result emits AnswerAbstained to the outbox.
 */
@SpringBootTest
class AiDecisionIT extends PostgresContainerSupport {

    @Autowired CorpusIngestionService ingestion;
    @Autowired GenerationService generationService;
    @Autowired VerificationService verificationService;
    @Autowired AiDecisionService decisionService;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        ingestion.seed();
    }

    @Test
    void decide_withKnownLowScores_emitsAnswerAbstained() {
        UUID questionId = UUID.randomUUID();
        // Manually construct a below-threshold VerificationResult to force ABSTAINED
        VerificationResult lowResult = new VerificationResult(
                UUID.randomUUID(), questionId, 0.20, 0.30, 0.25, 0.10, 0.2125);

        DecisionOutcome outcome = decisionService.decide(questionId, lowResult, "UNKNOWN");

        assertThat(outcome).isEqualTo(DecisionOutcome.ABSTAINED);
        assertThat(countOutbox(questionId, AnswerAbstained.TYPE)).isEqualTo(1);
        assertThat(countOutbox(questionId, AnswerProduced.TYPE)).isZero();
    }

    @Test
    void decide_withKnownHighScores_emitsAnswerProduced() {
        UUID questionId = UUID.randomUUID();
        // Manually construct an above-threshold VerificationResult to force PRODUCED
        VerificationResult highResult = new VerificationResult(
                UUID.randomUUID(), questionId, 0.95, 0.90, 0.92, 1.00, 0.9425);

        DecisionOutcome outcome = decisionService.decide(questionId, highResult, "PHYSICS");

        assertThat(outcome).isEqualTo(DecisionOutcome.PRODUCED);
        assertThat(countOutbox(questionId, AnswerProduced.TYPE)).isEqualTo(1);
        assertThat(countOutbox(questionId, AnswerAbstained.TYPE)).isZero();
    }

    @Test
    void decide_realPipelineAnswer_emitsExactlyOneDecisionEvent() {
        UUID questionId = UUID.randomUUID();
        String questionText = "Explain Newton second law with force mass acceleration examples";
        CandidateAnswer candidate = generationService.generate(questionId, questionText);
        VerificationResult result = verificationService.verify(
                questionId, questionText, "PHYSICS", candidate);

        DecisionOutcome outcome = decisionService.decide(questionId, result, "PHYSICS");

        assertThat(outcome).isNotNull();
        long producedCount = countOutbox(questionId, AnswerProduced.TYPE);
        long abstainedCount = countOutbox(questionId, AnswerAbstained.TYPE);
        assertThat(producedCount + abstainedCount)
                .as("exactly one decision event emitted for outcome " + outcome)
                .isEqualTo(1);
    }

    private long countOutbox(UUID questionId, String eventType) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = :type AND aggregate_id = :qId",
                new MapSqlParameterSource()
                        .addValue("type", eventType)
                        .addValue("qId", questionId),
                Long.class);
        return count == null ? 0 : count;
    }
}
