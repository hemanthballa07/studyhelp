package com.platform.qc;

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
 * Slice 7 acceptance: dispatching an AnswerSubmitted event inserts a qc_reviews row and emits
 * exactly one QC event (QcPassed, QcFailed, or RevisionRequested) to the outbox.
 */
@SpringBootTest
class QcServiceIT extends PostgresContainerSupport {

    @Autowired
    EventDispatcher dispatcher;

    @Autowired
    OutboxStore outbox;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void dispatchingAnswerSubmittedInsertsQcReviewAndEmitsQcEvent() {
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID expertId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        // put question in SUBMITTED state so LifecycleEventHandler can transition it
        jdbc.update("""
                INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at, version)
                VALUES (?, ?, 'PHYSICS', 'Test Q', 'Body', CAST('SUBMITTED' AS question_state),
                        now() + interval '1 day', 0)
                """, questionId, studentId);

        // well-structured body so the QC event emitted is predictable (PASS expected)
        String body = "1. Given mass=2 kg and acceleration=5 m/s, apply $F=ma$. 2. Therefore F=10 N.";
        String payload = """
                {"answerId":"%s","questionId":"%s","expertId":"%s","body":"%s","subjectCode":"PHYSICS"}
                """.formatted(answerId, questionId, expertId, body);

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), answerId, "Answer", "AnswerSubmitted", payload, Instant.now());
        outbox.append(event);
        dispatcher.dispatch(event);

        Integer reviews = jdbc.queryForObject(
                "SELECT count(*) FROM qc_reviews WHERE answer_id = ?", Integer.class, answerId);
        assertThat(reviews).isEqualTo(1);

        Integer qcEvents = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = ?"
                + " AND event_type IN ('QcPassed','QcFailed','RevisionRequested')",
                Integer.class, answerId);
        assertThat(qcEvents).isEqualTo(1);
    }
}
