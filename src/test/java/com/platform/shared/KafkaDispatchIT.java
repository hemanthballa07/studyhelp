package com.platform.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end test for the Kafka dispatcher swap. Uses an embedded Kafka broker to verify that the
 * relay publishes events to the topic, the consumer delivers them to handlers exactly once
 * (idempotency via processed_events), and the outbox row is marked published.
 */
@SpringBootTest
@ActiveProfiles("kafka")
@EmbeddedKafka(partitions = 1, topics = KafkaDispatchIT.TOPIC)
@DirtiesContext
class KafkaDispatchIT extends PostgresContainerSupport {

    static final String TOPIC = "platform.events";

    @Autowired OutboxStore outbox;
    @Autowired OutboxRelay relay;
    @Autowired EventDispatcher dispatcher;
    @Autowired RecordingHandler handler;
    @Autowired JdbcTemplate jdbc;

    @Test
    void relayPublishesToKafkaAndConsumerDeliversOnce() {
        UUID eventId = UUID.randomUUID();
        outbox.append(new OutboxEvent(
                eventId, UUID.randomUUID(), "Test", "TestEvent", "{\"k\":\"v\"}", Instant.now()));

        relay.relayPending();

        await().atMost(10, TimeUnit.SECONDS).until(() -> handler.countFor(eventId) == 1);

        Integer processed = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer = 'kafka-test-consumer' AND event_id = ?",
                Integer.class, eventId);
        assertThat(processed).isEqualTo(1);

        Object publishedAt = jdbc.queryForObject(
                "SELECT published_at FROM outbox WHERE event_id = ?", Object.class, eventId);
        assertThat(publishedAt).isNotNull();
    }

    @Test
    void replayIsNoOp() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId, UUID.randomUUID(), "Test", "TestEvent", "{}", Instant.now());
        outbox.append(event);

        // Dispatch twice: both produce a Kafka message; consumer must process each exactly once.
        dispatcher.dispatch(event);
        dispatcher.dispatch(event);

        await().atMost(10, TimeUnit.SECONDS).until(() -> handler.countFor(eventId) >= 1);
        // Brief pause to allow the second Kafka message to be consumed before asserting.
        Thread.sleep(500);

        assertThat(handler.countFor(eventId)).isEqualTo(1);
    }

    @TestConfiguration
    static class Config {
        @Bean
        RecordingHandler kafkaRecordingHandler() {
            return new RecordingHandler();
        }
    }

    static class RecordingHandler implements EventHandler {
        private final List<UUID> handled = new CopyOnWriteArrayList<>();

        @Override
        public String consumerName() {
            return "kafka-test-consumer";
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
