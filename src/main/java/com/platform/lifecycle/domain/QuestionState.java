package com.platform.lifecycle.domain;

/**
 * The lifecycle states of a question (master-design section 6.1). Persisted as the Postgres
 * {@code question_state} enum; the constant names match the enum labels in {@code V2__lifecycle_outbox.sql}
 * exactly, so they bind directly as the column value.
 */
public enum QuestionState {
    POSTED,
    DEDUP_CHECKING,
    ROUTED,
    CLAIMABLE,
    CLAIMED,
    IN_PROGRESS,
    SUBMITTED,
    IN_REVIEW,
    REVISION_REQUESTED,
    DELIVERED,
    REJECTED,
    CLAIM_EXPIRED,
    RATED
}
