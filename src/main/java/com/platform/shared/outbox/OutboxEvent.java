package com.platform.shared.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the transactional outbox, in Java form. Field names are camelCase to match the
 * published event envelope (master-design section 5); the {@code outbox} table columns are
 * snake_case. {@link OutboxStore} is the translation boundary between the two.
 *
 * @param payload the event body as a JSON string (stored in the {@code jsonb payload} column)
 */
public record OutboxEvent(
        UUID eventId,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        String payload,
        Instant occurredAt) {
}
