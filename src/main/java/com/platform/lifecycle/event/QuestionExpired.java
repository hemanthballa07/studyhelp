package com.platform.lifecycle.event;

import java.util.UUID;

/**
 * Domain event: a claim lease expired and the question was re-opened to the claimable pool
 * (master-design 6.4). Emitted by lifecycle (the only writer of question state). Carries the subject
 * so the expert portal can re-add the question to its claimable-queue projection.
 */
public record QuestionExpired(UUID questionId, String subject, UUID previousClaimedBy) {

    /** Wire type, used as the {@code outbox.event_type} and {@code question_events.event_type}. */
    public static final String TYPE = "QuestionExpired";
}
