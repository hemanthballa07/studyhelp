package com.platform.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.shared.dispatcher.EventDispatcher;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxRelay;
import com.platform.shared.outbox.OutboxStore;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The relay drains unpublished outbox rows to the registered consumers, records idempotency, and
 * marks rows published; redelivering an event is a no-op (Slice 03 acceptance: dispatcher delivery +
 * replay no-op via the {@code processed_events} unique constraint).
 */
@SpringBootTest
class OutboxDispatchIT extends PostgresContainerSupport {

    @Autowired
    OutboxStore outbox;

    @Autowired
    OutboxRelay relay;

    @Autowired
    EventDispatcher dispatcher;

    @Autowired
    RecordingHandler handler;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void relayDeliversUnpublishedEventToConsumerAndMarksItPublished() {
        UUID eventId = UUID.randomUUID();
        outbox.append(new OutboxEvent(
                eventId, UUID.randomUUID(), "Question", "QuestionPosted", "{\"k\":\"v\"}", Instant.now()));

        relay.relayPending();

        assertThat(handler.countFor(eventId)).isEqualTo(1);
        Integer processed = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer = 'test-consumer' AND event_id = ?",
                Integer.class, eventId);
        assertThat(processed).isEqualTo(1);
        Object publishedAt = jdbc.queryForObject(
                "SELECT published_at FROM outbox WHERE event_id = ?", Object.class, eventId);
        assertThat(publishedAt).isNotNull();
    }

    @Test
    void redeliveringAnEventIsANoOp() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId, UUID.randomUUID(), "Question", "QuestionPosted", "{}", Instant.now());
        outbox.append(event);

        dispatcher.dispatch(event);
        dispatcher.dispatch(event);

        assertThat(handler.countFor(eventId)).isEqualTo(1);
        Integer processed = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
        assertThat(processed).isEqualTo(1);
    }

    @TestConfiguration
    static class Config {
        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    /** A genuine in-process consumer that records which events it handled. */
    static class RecordingHandler implements EventHandler {
        private final List<UUID> handled = new CopyOnWriteArrayList<>();

        @Override
        public String consumerName() {
            return "test-consumer";
        }

        @Override
        public void handle(OutboxEvent event) {
            handled.add(event.eventId());
        }

        long countFor(UUID eventId) {
            return handled.stream().filter(eventId::equals).count();
        }
    }
}
