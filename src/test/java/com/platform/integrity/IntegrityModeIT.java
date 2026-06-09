package com.platform.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.integrity.app.IntegrityService;
import com.platform.shared.integrity.IntegrityDecision;
import com.platform.shared.integrity.IntegrityDecision.Mode;
import com.platform.support.PostgresContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Integration test: verifies IntegrityService appends the correct outbox events
 * for exam-like and normal prompts on a real Postgres container.
 */
@SpringBootTest
class IntegrityModeIT extends PostgresContainerSupport {

    @Autowired IntegrityService integrityService;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void examKeywordPrompt_appendsQuestionFlaggedToOutbox() {
        UUID questionId = UUID.randomUUID();

        // "exam" + "quiz" = 2 keyword matches → PEDAGOGICAL threshold
        IntegrityDecision decision = integrityService.assess(questionId,
                "I have an exam question and a quiz to prepare for");

        assertThat(decision.mode()).isEqualTo(Mode.PEDAGOGICAL);
        assertThat(countOutbox(questionId, "QuestionFlagged")).isEqualTo(1);
        assertThat(countOutbox(questionId, "RefusalIssued")).isZero();
    }

    @Test
    void normalPrompt_doesNotAppendToOutbox() {
        UUID questionId = UUID.randomUUID();

        IntegrityDecision decision = integrityService.assess(questionId,
                "Explain Newton second law of motion with examples.");

        assertThat(decision.mode()).isEqualTo(Mode.NORMAL);
        assertThat(countOutbox(questionId, "QuestionFlagged")).isZero();
        assertThat(countOutbox(questionId, "RefusalIssued")).isZero();
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
