package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.shared.dispatcher.EventDispatcher;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Slice 7 lifecycle acceptance: QC events drive question state transitions correctly.
 * SUBMITTED -> IN_REVIEW on AnswerSubmitted.
 * IN_REVIEW  -> DELIVERED (+ QuestionDelivered outbox) on QcPassed.
 * IN_REVIEW  -> REVISION_REQUESTED on RevisionRequested.
 */
@SpringBootTest
class LifecycleQcTransitionIT extends PostgresContainerSupport {

    @Autowired
    EventDispatcher dispatcher;

    @Autowired
    OutboxStore outbox;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void answerSubmittedTransitionsQuestionFromSubmittedToInReview() {
        UUID questionId = UUID.randomUUID();
        insertQuestion(questionId, "SUBMITTED");

        UUID answerId = UUID.randomUUID();
        String payload = answerSubmittedPayload(answerId, questionId, UUID.randomUUID());
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), answerId, "Answer", "AnswerSubmitted", payload, Instant.now());
        outbox.append(event);
        dispatcher.dispatch(event);

        assertThat(queryState(questionId)).isEqualTo("IN_REVIEW");
    }

    @Test
    void qcPassedTransitionsQuestionFromInReviewToDeliveredAndEmitsOutboxEvent() {
        UUID questionId = UUID.randomUUID();
        insertQuestion(questionId, "IN_REVIEW");

        UUID answerId = UUID.randomUUID();
        String payload = qcResultPayload(answerId, questionId, UUID.randomUUID());
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), answerId, "Answer", "QcPassed", payload, Instant.now());
        outbox.append(event);
        dispatcher.dispatch(event);

        assertThat(queryState(questionId)).isEqualTo("DELIVERED");

        Integer delivered = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = 'QuestionDelivered'",
                Integer.class, questionId);
        assertThat(delivered).isEqualTo(1);
    }

    @Test
    void revisionRequestedTransitionsQuestionFromInReviewToRevisionRequested() {
        UUID questionId = UUID.randomUUID();
        insertQuestion(questionId, "IN_REVIEW");

        UUID answerId = UUID.randomUUID();
        String payload = qcResultPayload(answerId, questionId, UUID.randomUUID());
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), answerId, "Answer", "RevisionRequested", payload, Instant.now());
        outbox.append(event);
        dispatcher.dispatch(event);

        assertThat(queryState(questionId)).isEqualTo("REVISION_REQUESTED");
    }

    private void insertQuestion(UUID questionId, String state) {
        jdbc.update("""
                INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at, version)
                VALUES (?, ?, 'MATH', 'Q', 'B', CAST(? AS question_state), now() + interval '1 day', 0)
                """, questionId, UUID.randomUUID(), state);
    }

    private String queryState(UUID questionId) {
        return jdbc.queryForObject(
                "SELECT state::text FROM questions WHERE id = ?", String.class, questionId);
    }

    private String answerSubmittedPayload(UUID answerId, UUID questionId, UUID expertId) {
        return """
                {"answerId":"%s","questionId":"%s","expertId":"%s","body":"test body","subjectCode":"MATH"}
                """.formatted(answerId, questionId, expertId);
    }

    private String qcResultPayload(UUID answerId, UUID questionId, UUID expertId) {
        return """
                {"answerId":"%s","questionId":"%s","expertId":"%s"}
                """.formatted(answerId, questionId, expertId);
    }
}
