package com.platform.shared.dispatcher;

import com.platform.shared.outbox.OutboxEvent;

/**
 * A consumer of dispatched domain events. Each handler names itself so the dispatcher can record
 * per-consumer idempotency in {@code processed_events(consumer, event_id)}. Handlers must be
 * idempotent: {@link #handle} may be called again for an event a prior attempt failed to commit.
 */
public interface EventHandler {

    /** Stable identifier for this consumer, used as the {@code processed_events.consumer} key. */
    String consumerName();

    /** Apply the event. Throwing rolls back the dispatch so the event is retried later. */
    void handle(OutboxEvent event);
}
