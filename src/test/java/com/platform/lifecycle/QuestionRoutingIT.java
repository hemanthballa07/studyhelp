package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.lifecycle.app.PostQuestionCommand;
import com.platform.lifecycle.app.QuestionPostingService;
import com.platform.shared.dispatcher.EventDispatcher;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The lifecycle consumer drives a posted question to CLAIMABLE and emits QuestionRouted when it
 * consumes QuestionPosted; redelivering the same QuestionPosted is a no-op (master-design 6.1, 5).
 * This exercises the real Slice 3 dispatcher with lifecycle as its first cross-context consumer.
 */
@SpringBootTest
class QuestionRoutingIT extends PostgresContainerSupport {

    @Autowired
    QuestionPostingService posting;

    @Autowired
    EventDispatcher dispatcher;

    @Autowired
    OutboxStore outbox;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clearCorpus() {
        // corpus_index persists across tests in the singleton container; routing tests are
        // not testing search, so a stale index would trigger false-positive dedup short-circuits.
        jdbc.execute("DELETE FROM corpus_index");
    }

    @Test
    void consumingQuestionPostedDrivesToClaimableAndEmitsQuestionRouted() {
        UUID questionId = post();

        dispatcher.dispatch(questionPostedEvent(questionId));

        assertThat(state(questionId)).isEqualTo("CLAIMABLE");
        assertThat(routedOutboxCount(questionId)).isEqualTo(1);
    }

    @Test
    void replayingQuestionPostedIsANoOp() {
        UUID questionId = post();
        OutboxEvent posted = questionPostedEvent(questionId);

        dispatcher.dispatch(posted);
        dispatcher.dispatch(posted);

        assertThat(state(questionId)).isEqualTo("CLAIMABLE");
        assertThat(routedOutboxCount(questionId)).isEqualTo(1);

        Integer events = jdbc.queryForObject(
                "SELECT count(*) FROM question_events WHERE question_id = ?", Integer.class, questionId);
        assertThat(events).as("QuestionPosted plus three drive transitions, once each").isEqualTo(4);

        Integer processed = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer = 'lifecycle' AND event_id = ?",
                Integer.class, posted.eventId());
        assertThat(processed).isEqualTo(1);
    }

    private UUID post() {
        return posting.post(new PostQuestionCommand(
                UUID.randomUUID(), "math", "Limit of sin x over x", "Show the steps",
                Instant.parse("2030-01-01T00:00:00Z")));
    }

    private OutboxEvent questionPostedEvent(UUID questionId) {
        return outbox.fetchUnpublished(1000).stream()
                .filter(e -> questionId.equals(e.aggregateId()) && "QuestionPosted".equals(e.eventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no QuestionPosted outbox row for " + questionId));
    }

    private String state(UUID id) {
        return jdbc.queryForObject("SELECT state::text FROM questions WHERE id = ?", String.class, id);
    }

    private Integer routedOutboxCount(UUID id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = 'QuestionRouted'",
                Integer.class, id);
    }
}
