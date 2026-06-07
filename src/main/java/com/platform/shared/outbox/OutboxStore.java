package com.platform.shared.outbox;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the transactional outbox. Producers {@link #append} an event inside the same
 * transaction that mutates their tables; the relay reads {@link #fetchUnpublished} rows, dispatches
 * them, and then {@link #markPublished}. Maps the camelCase {@link OutboxEvent} to the snake_case
 * {@code outbox} columns (master-design section 5).
 */
public interface OutboxStore {

    void append(OutboxEvent event);

    /** Unpublished events, oldest first, locked for the caller so concurrent relays do not collide. */
    List<OutboxEvent> fetchUnpublished(int limit);

    void markPublished(UUID eventId);
}
