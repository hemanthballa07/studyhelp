package com.platform.shared.claim;

import java.time.Instant;
import java.util.UUID;

/**
 * The outcome of a successful {@link ClaimPort#claim} call: the question now held by the claiming
 * expert, with its new lease deadline and version. Lives in {@code shared} so the calling context can
 * use it without importing a lifecycle type.
 */
public record ClaimedQuestion(
        UUID questionId, String subject, UUID claimedBy, Instant claimExpiresAt, long version) {
}
