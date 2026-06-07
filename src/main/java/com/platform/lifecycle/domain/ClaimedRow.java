package com.platform.lifecycle.domain;

import java.time.Instant;
import java.util.UUID;

/** The row returned by an atomic claim (master-design 6.2): the now-CLAIMED question's key fields. */
public record ClaimedRow(
        UUID questionId, String subject, UUID claimedBy, Instant claimExpiresAt, long version) {
}
