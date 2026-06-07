package com.platform.shared.dispatcher;

import com.platform.shared.outbox.OutboxEvent;

/**
 * Delivers an outbox event to its consumers. This interface is the swap point for Slice 11: the
 * in-process implementation invokes {@link EventHandler}s directly, while a later Kafka
 * implementation publishes to a topic. The event contract and outbox schema do not change across
 * the swap (master-design section 5).
 */
public interface EventDispatcher {

    /** Deliver one event idempotently to every consumer. */
    void dispatch(OutboxEvent event);
}
