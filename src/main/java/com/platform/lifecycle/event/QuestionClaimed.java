package com.platform.lifecycle.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: a question was claimed by an expert (CLAIMABLE -> CLAIMED, master-design 6.1/6.2).
 * Emitted by lifecycle, the only writer of question state; the expert portal consumes it (by wire
 * type) to drop the question from its claimable-queue projection.
 */
public record QuestionClaimed(UUID questionId, String subject, UUID claimedBy, Instant claimExpiresAt) {

    /** Wire type, used as the {@code outbox.event_type} and {@code question_events.event_type}. */
    public static final String TYPE = "QuestionClaimed";
}
