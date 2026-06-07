package com.platform.lifecycle.domain;

import java.time.Instant;
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
}
