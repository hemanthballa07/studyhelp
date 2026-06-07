package com.platform.expertportal.event;

import java.util.UUID;

/**
 * Domain event: a claim attempt found nothing claimable for the subject (master-design 4). Distinct
 * from a state change (nothing is written to a question); it signals an idle expert / empty queue.
 */
public record QuestionSkipped(UUID expertId, String subject) {

    /** Wire type, used as the {@code outbox.event_type}. */
    public static final String TYPE = "QuestionSkipped";
}
