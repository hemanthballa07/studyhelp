package com.platform.shared.dispatcher;

import java.util.UUID;

/**
 * Records which {@code (consumer, event_id)} pairs have been processed, backed by a unique
 * constraint so a replay is a no-op. This is the idempotency ledger every consumer shares.
 */
public interface ProcessedEventStore {

    /**
     * Claim an event for a consumer.
     *
     * @return {@code true} if this is the first time the consumer sees the event (the caller should
     *     process it); {@code false} if it was already recorded (the caller should skip it).
     */
    boolean markProcessed(String consumer, UUID eventId);
}
