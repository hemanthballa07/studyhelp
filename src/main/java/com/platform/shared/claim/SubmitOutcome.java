package com.platform.shared.claim;

/**
 * Result of a conditional submit (master-design 6.3). {@code SUBMITTED} means the question was still
 * in progress under a valid lease held by the caller; {@code STALE} means the conditional update
 * matched no row (lease expired, wrong owner, or wrong state), so the answer must be flagged stale and
 * nothing payout-triggering may be emitted.
 */
public enum SubmitOutcome {
    SUBMITTED,
    STALE
}
