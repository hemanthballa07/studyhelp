package com.platform.expertportal.event;

import java.util.UUID;

/**
 * Domain event: an expert attempted a claim. Emitted on every attempt (master-design 4); the
 * persisted form is the {@code claim_attempts} row. {@code outcome} is {@code WON} or {@code SKIPPED};
 * {@code questionId} is the claimed question on a win, or null on a skip.
 */
public record ExpertClaimAttempted(
        UUID attemptId, UUID expertId, String subject, String outcome, UUID questionId) {

    /** Wire type, used as the {@code outbox.event_type}. */
    public static final String TYPE = "ExpertClaimAttempted";
}
