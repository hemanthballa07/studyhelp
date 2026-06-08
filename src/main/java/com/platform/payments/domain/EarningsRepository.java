package com.platform.payments.domain;

import java.util.UUID;

public interface EarningsRepository {

    /**
     * Inserts one earning row. Returns 1 when the row is new, 0 when the source_event_id already
     * exists (ON CONFLICT DO NOTHING).
     */
    int accrue(UUID sourceEventId, UUID questionId, UUID expertId, int amountCents);
}
