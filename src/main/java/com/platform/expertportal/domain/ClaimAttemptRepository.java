package com.platform.expertportal.domain;

import java.util.UUID;

/** Append-only log of claim attempts (the persisted form of {@code ExpertClaimAttempted}). */
public interface ClaimAttemptRepository {

    /** Record one attempt. {@code questionId} is the won question, or null on a skip. */
    void record(UUID id, UUID expertId, String subject, String outcome, UUID questionId);
}
