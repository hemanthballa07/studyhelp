package com.platform.lifecycle.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes to the lifecycle's canonical tables. Lifecycle is the only writer of question state
 * (master-design section 3). {@code question_events} is append-only: this interface exposes inserts
 * only, with no update or delete path. State changes go through {@link #applyTransition}, never a
 * blind UPDATE.
 */
public interface QuestionRepository {

    void insertPostedQuestion(
            UUID id, UUID studentId, String subject, String title, String body, Instant deadlineAt);

    void appendEvent(
            UUID eventId, UUID questionId, String eventType, String fromState, String toState, String payloadJson);

    /** The question's current state and version, or empty if no such question exists. */
    Optional<QuestionSnapshot> find(UUID id);

    /**
     * Apply a state transition guarded by optimistic concurrency: a single conditional UPDATE that
     * matches only when the row is still in {@code from} at {@code expectedVersion}, bumping the
     * version by one (master-design sections 6.1 and 8). Returns the new version when exactly one row
     * changed, or empty when none did (a stale version, the wrong from-state, or a missing row), so
     * the caller rejects the lost transition rather than silently continuing.
     */
    Optional<Long> applyTransition(UUID id, QuestionState from, QuestionState to, long expectedVersion);

    /**
     * Atomically claim the next claimable question for {@code subject} (master-design 6.2): one
     * statement selects the highest-priority, oldest claimable, not-yet-overdue row with {@code FOR
     * UPDATE SKIP LOCKED} and flips it to CLAIMED with a fresh lease, so concurrent claimers skip a
     * locked row and never claim the same question. Returns the claimed row, or empty when nothing is
     * currently claimable. This is the work-queue claim, distinct from the optimistic {@link
     * #applyTransition} version guard.
     */
    Optional<ClaimedRow> claimNextClaimable(String subject, UUID expertId, int leaseMinutes);

    /** Start work: CLAIMED -> IN_PROGRESS, only for the owner under a live lease. True if it transitioned. */
    boolean startWork(UUID id, UUID expertId);

    /**
     * Conditional submit (master-design 6.3): IN_PROGRESS -> SUBMITTED only while {@code expertId} owns
     * the claim and the lease is still valid. False (0 rows) means the submit is stale (no ghost delivery).
     */
    boolean submitIfOwned(UUID id, UUID expertId);

    /**
     * Expire up to {@code batchSize} overdue leases (master-design 6.4): CLAIMED/IN_PROGRESS rows with
     * {@code claim_expires_at <= now()} are grabbed with {@code FOR UPDATE SKIP LOCKED} and flipped to
     * CLAIM_EXPIRED in one statement, so concurrent sweepers take disjoint rows. Returns the expired rows.
     */
    List<ExpiredLease> expireLeasedBatch(int batchSize);

    /** Re-open an expired question: CLAIM_EXPIRED -> CLAIMABLE, clearing the claim fields. */
    boolean reopenExpired(UUID id);
}
