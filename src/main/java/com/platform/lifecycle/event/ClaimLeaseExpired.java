package com.platform.lifecycle.event;

import java.util.UUID;

/**
 * Domain event: the lease an expert held on a question lapsed (master-design 6.4). Slice-local: it
 * pairs with {@link QuestionExpired} and lets the expert side react (drop the question from an
 * expert's active set) once a consumer exists.
 */
public record ClaimLeaseExpired(UUID questionId, UUID expertId) {

    /** Wire type, used as the {@code outbox.event_type}. */
    public static final String TYPE = "ClaimLeaseExpired";
}
