package com.platform.expertportal.domain;

import java.util.List;
import java.util.UUID;

/**
 * The claimable-queue read-model projection. Maintained from lifecycle events (a question is added on
 * {@code QuestionRouted}, removed on {@code QuestionClaimed}); it is a display view only. The claim
 * never reads it (the atomic claim reads the canonical questions table via the ClaimPort), so a stale
 * projection can never cause a double-claim.
 */
public interface ClaimableQueueRepository {

    /** Add a question to the queue; idempotent (a redelivered route is a no-op). */
    void add(UUID questionId, String subject);

    /**
     * Remove a question from the queue; idempotent (removing an absent row is a no-op). Returns the
     * number of rows removed so the caller can detect a {@code QuestionClaimed} for a question that was
     * never projected (read model diverged from canonical state).
     */
    int remove(UUID questionId);

    /** Claimable questions for a subject, oldest first, capped at {@code limit}. */
    List<ClaimableQuestionView> findBySubject(String subject, int limit);
}
