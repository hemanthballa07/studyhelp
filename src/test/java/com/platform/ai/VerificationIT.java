package com.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.ai.app.CorpusIngestionService;
import com.platform.ai.app.GenerationService;
import com.platform.ai.app.VerificationService;
import com.platform.ai.domain.VerificationRepository;
import com.platform.ai.domain.VerificationResult;
import com.platform.ai.event.VerificationCompleted;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.support.PostgresContainerSupport;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Verifies the 4-signal verifier end-to-end on a real Postgres container:
 * 1. Each signal produces a 0–1 score.
 * 2. The verification row is persisted.
 * 3. A VerificationCompleted outbox event is written.
 * 4. Repeated calls are idempotent.
 */
@SpringBootTest
class VerificationIT extends PostgresContainerSupport {

    @Autowired CorpusIngestionService ingestion;
    @Autowired GenerationService generationService;
    @Autowired VerificationService verificationService;
    @Autowired VerificationRepository verificationRepo;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        ingestion.seed();
    }

    @Test
    void verify_producesAllFourScoresInRange() {
        UUID questionId = UUID.randomUUID();
        String questionText = "Explain Newton's second law with force mass acceleration";
        CandidateAnswer candidate = generationService.generate(questionId, questionText);

        VerificationResult result = verificationService.verify(
                questionId, questionText, "PHYSICS", candidate);

        assertThat(result.groundednessScore()).isBetween(0.0, 1.0);
        assertThat(result.structuralScore()).isBetween(0.0, 1.0);
        assertThat(result.consistencyScore()).isBetween(0.0, 1.0);
        assertThat(result.mathScore()).isBetween(0.0, 1.0);
        assertThat(result.aggregateScore()).isBetween(0.0, 1.0);
    }

    @Test
    void verify_persistsVerificationRow() {
        UUID questionId = UUID.randomUUID();
        CandidateAnswer candidate = generationService.generate(
                questionId, "quadratic formula roots discriminant");

        verificationService.verify(questionId, "quadratic formula roots discriminant", "MATH", candidate);

        Optional<VerificationResult> stored = verificationRepo.findByQuestionId(questionId);
        assertThat(stored).isPresent();
        assertThat(stored.get().questionId()).isEqualTo(questionId);
        assertThat(stored.get().aggregateScore()).isBetween(0.0, 1.0);
    }

    @Test
    void verify_emitsVerificationCompletedOutboxEvent() {
        UUID questionId = UUID.randomUUID();
        CandidateAnswer candidate = generationService.generate(
                questionId, "Newton second law");

        verificationService.verify(questionId, "Newton second law", "PHYSICS", candidate);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = :type AND aggregate_id = :qId",
                new MapSqlParameterSource()
                        .addValue("type", VerificationCompleted.TYPE)
                        .addValue("qId", questionId),
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void verify_idempotent_repeatedCallDoesNotDuplicateRow() {
        UUID questionId = UUID.randomUUID();
        String questionText = "Newton second law";
        CandidateAnswer candidate = generationService.generate(questionId, questionText);

        verificationService.verify(questionId, questionText, "PHYSICS", candidate);
        verificationService.verify(questionId, questionText, "PHYSICS", candidate);

        Integer verificationCount = jdbc.queryForObject(
                "SELECT count(*) FROM verifications WHERE question_id = :qId",
                new MapSqlParameterSource("qId", questionId),
                Integer.class);
        assertThat(verificationCount).isEqualTo(1);

        Integer outboxCount = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = :type AND aggregate_id = :qId",
                new MapSqlParameterSource()
                        .addValue("type", VerificationCompleted.TYPE)
                        .addValue("qId", questionId),
                Integer.class);
        assertThat(outboxCount).isEqualTo(1);
    }
}
