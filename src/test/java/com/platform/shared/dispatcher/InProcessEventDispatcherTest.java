package com.platform.shared.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.shared.outbox.OutboxEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Control-flow contract of the in-process dispatcher: deliver once, skip on replay, and never
 * swallow a handler failure. The real {@code processed_events} unique constraint and the
 * transactional rollback are exercised against Postgres in {@code OutboxDispatchIT} on CI; here a
 * fake store keeps the idempotency logic deterministic and fast.
 */
class InProcessEventDispatcherTest {

    @Test
    void deliversEventToEveryRegisteredHandler() {
        RecordingHandler search = new RecordingHandler("search");
        RecordingHandler portal = new RecordingHandler("expertportal");
        InProcessEventDispatcher dispatcher =
                new InProcessEventDispatcher(List.of(search, portal), new InMemoryProcessedEventStore());
        OutboxEvent event = sampleEvent();

        dispatcher.dispatch(event);

        assertThat(search.handled).containsExactly(event.eventId());
        assertThat(portal.handled).containsExactly(event.eventId());
    }

    @Test
    void redeliveringTheSameEventIsANoOpPerConsumer() {
        RecordingHandler search = new RecordingHandler("search");
        InProcessEventDispatcher dispatcher =
                new InProcessEventDispatcher(List.of(search), new InMemoryProcessedEventStore());
        OutboxEvent event = sampleEvent();

        dispatcher.dispatch(event);
        dispatcher.dispatch(event);

        assertThat(search.handled).containsExactly(event.eventId());
    }

    @Test
    void aHandlerFailurePropagatesAndIsNotSwallowed() {
        InProcessEventDispatcher dispatcher =
                new InProcessEventDispatcher(List.of(new FailingHandler("search")), new InMemoryProcessedEventStore());

        assertThatThrownBy(() -> dispatcher.dispatch(sampleEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("search");
    }

    private static OutboxEvent sampleEvent() {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "Question",
                "QuestionPosted", "{}", Instant.parse("2026-06-06T00:00:00Z"));
    }

    private static final class RecordingHandler implements EventHandler {
        private final String consumer;
        private final List<UUID> handled = new ArrayList<>();

        private RecordingHandler(String consumer) {
            this.consumer = consumer;
        }

        @Override
        public String consumerName() {
            return consumer;
        }

        @Override
        public void handle(OutboxEvent event) {
            handled.add(event.eventId());
        }
    }

    private static final class FailingHandler implements EventHandler {
        private final String consumer;

        private FailingHandler(String consumer) {
            this.consumer = consumer;
        }

        @Override
        public String consumerName() {
            return consumer;
        }

        @Override
        public void handle(OutboxEvent event) {
            throw new IllegalStateException("handler " + consumer + " blew up");
        }
    }

    /** In-memory stand-in for the JDBC {@code processed_events} table. */
    private static final class InMemoryProcessedEventStore implements ProcessedEventStore {
        private final Set<String> processed = new HashSet<>();

        @Override
        public boolean markProcessed(String consumer, UUID eventId) {
            return processed.add(consumer + ":" + eventId);
        }
    }
}
