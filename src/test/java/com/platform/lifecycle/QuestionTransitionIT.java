package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.lifecycle.app.LifecycleTransitionService;
import com.platform.lifecycle.domain.IllegalTransitionException;
import com.platform.lifecycle.domain.QuestionState;
import com.platform.lifecycle.domain.TransitionRejectedException;
import com.platform.support.PostgresContainerSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A legal transition applies and writes the audit row plus (for an emitting edge) the outbox row in
 * one transaction, sharing one event id; an illegal edge or a wrong from-state writes nothing
 * (master-design 6.1, 5).
 */
@SpringBootTest
class QuestionTransitionIT extends PostgresContainerSupport {

    @Autowired
    LifecycleTransitionService transitions;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void emittingTransitionWritesAuditAndOutboxRowSharingOneEventId() {
        UUID questionId = insertQuestion("DEDUP_CHECKING");
        String payload = "{\"questionId\":\"" + questionId + "\",\"subject\":\"math\"}";

        long newVersion = transitions.transition(
                questionId, QuestionState.DEDUP_CHECKING, QuestionState.ROUTED, 0L, "QuestionRouted", payload, true);

        assertThat(newVersion).isEqualTo(1L);
        assertThat(state(questionId)).isEqualTo("ROUTED");

        UUID auditEventId = jdbc.queryForObject(
                "SELECT id FROM question_events WHERE question_id = ? AND event_type = 'QuestionRouted'"
                        + " AND from_state = 'DEDUP_CHECKING' AND to_state = 'ROUTED'",
                UUID.class, questionId);
        UUID outboxEventId = jdbc.queryForObject(
                "SELECT event_id FROM outbox WHERE aggregate_id = ? AND event_type = 'QuestionRouted'",
                UUID.class, questionId);
        assertThat(outboxEventId).isEqualTo(auditEventId);
    }

    @Test
    void wrongFromStateIsRejectedAndWritesNothing() {
        UUID questionId = insertQuestion("POSTED");

        assertThatThrownBy(() -> transitions.transition(
                questionId, QuestionState.DEDUP_CHECKING, QuestionState.ROUTED, 0L, "QuestionRouted", "{}", true))
                .isInstanceOf(TransitionRejectedException.class);

        assertThat(state(questionId)).isEqualTo("POSTED");
        assertThat(version(questionId)).isEqualTo(0L);
        assertThat(auditCount(questionId)).isZero();
        assertThat(outboxCount(questionId)).isZero();
    }

    @Test
    void illegalEdgeIsRejectedByTheStateMachineAndWritesNothing() {
        UUID questionId = insertQuestion("POSTED");

        assertThatThrownBy(() -> transitions.transition(
                questionId, QuestionState.POSTED, QuestionState.CLAIMABLE, 0L, "X", "{}", false))
                .isInstanceOf(IllegalTransitionException.class);

        assertThat(state(questionId)).isEqualTo("POSTED");
        assertThat(version(questionId)).isEqualTo(0L);
        assertThat(auditCount(questionId)).isZero();
    }

    private UUID insertQuestion(String state) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at)"
                        + " VALUES (?, ?, 'math', 'title', 'body', CAST(? AS question_state), ?)",
                id, UUID.randomUUID(), state, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        return id;
    }

    private String state(UUID id) {
        return jdbc.queryForObject("SELECT state::text FROM questions WHERE id = ?", String.class, id);
    }

    private Long version(UUID id) {
        return jdbc.queryForObject("SELECT version FROM questions WHERE id = ?", Long.class, id);
    }

    private Integer auditCount(UUID id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM question_events WHERE question_id = ? AND from_state IS NOT NULL",
                Integer.class, id);
    }

    private Integer outboxCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_id = ?", Integer.class, id);
    }
}
