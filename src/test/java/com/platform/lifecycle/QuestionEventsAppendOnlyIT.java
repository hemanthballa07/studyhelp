package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.lifecycle.app.PostQuestionCommand;
import com.platform.lifecycle.app.QuestionPostingService;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code question_events} is the immutable audit log: a database trigger rejects UPDATE and DELETE,
 * so there is no mutation path (Slice 03 acceptance: "question_events is append-only").
 */
@SpringBootTest
class QuestionEventsAppendOnlyIT extends PostgresContainerSupport {

    @Autowired
    QuestionPostingService posting;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void updatingAQuestionEventIsRejected() {
        UUID questionId = posting.post(new PostQuestionCommand(
                UUID.randomUUID(), "math", "t", "b", Instant.parse("2030-01-01T00:00:00Z")));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE question_events SET event_type = 'tampered' WHERE question_id = ?", questionId))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void deletingAQuestionEventIsRejected() {
        UUID questionId = posting.post(new PostQuestionCommand(
                UUID.randomUUID(), "math", "t", "b", Instant.parse("2030-01-01T00:00:00Z")));

        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM question_events WHERE question_id = ?", questionId))
                .hasStackTraceContaining("append-only");

        Integer remaining = jdbc.queryForObject(
                "SELECT count(*) FROM question_events WHERE question_id = ?", Integer.class, questionId);
        assertThat(remaining).isEqualTo(1);
    }
}
