package com.platform.expertportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.expertportal.app.ExpertPortalEventHandler;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The claimable-queue projection: a question is added on {@code QuestionRouted} and removed on
 * {@code QuestionClaimed}, and a redelivered route is a no-op (idempotent). Drives the handler
 * directly with synthetic outbox events.
 */
@SpringBootTest
class ExpertPortalProjectionIT extends PostgresContainerSupport {

    @Autowired
    ExpertPortalEventHandler handler;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void routedAddsToTheQueueIdempotentlyAndClaimedRemoves() {
        UUID questionId = UUID.randomUUID();
        String subject = "proj-" + UUID.randomUUID();

        handler.handle(event(questionId, "QuestionRouted", subject));
        assertThat(inQueue(questionId)).as("routed question is queued").isEqualTo(1);

        handler.handle(event(questionId, "QuestionRouted", subject));
        assertThat(inQueue(questionId)).as("redelivered route does not duplicate").isEqualTo(1);

        handler.handle(event(questionId, "QuestionClaimed", subject));
        assertThat(inQueue(questionId)).as("claimed question leaves the queue").isZero();
    }

    private OutboxEvent event(UUID questionId, String type, String subject) {
        String payload = "{\"questionId\":\"" + questionId + "\",\"subject\":\"" + subject + "\"}";
        return new OutboxEvent(UUID.randomUUID(), questionId, "Question", type, payload, Instant.now());
    }

    private int inQueue(UUID questionId) {
        Integer c = jdbc.queryForObject(
                "SELECT count(*) FROM claimable_questions WHERE question_id = ?", Integer.class, questionId);
        return c == null ? 0 : c;
    }
}
