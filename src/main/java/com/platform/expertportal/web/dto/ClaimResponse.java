package com.platform.expertportal.web.dto;

import com.platform.shared.claim.ClaimedQuestion;
import java.time.Instant;
import java.util.UUID;

/** Body of a successful {@code POST /api/claims}: the question the expert now holds. */
public record ClaimResponse(
        UUID questionId, String subject, UUID claimedBy, Instant claimExpiresAt, long version) {

    public static ClaimResponse from(ClaimedQuestion claimed) {
        return new ClaimResponse(claimed.questionId(), claimed.subject(), claimed.claimedBy(),
                claimed.claimExpiresAt(), claimed.version());
    }
}
