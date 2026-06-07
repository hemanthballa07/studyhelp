package com.platform.lifecycle.domain;

import java.util.UUID;

/**
 * Thrown when a version-guarded transition matched no row: the question already moved on (stale
 * version or a different state) or does not exist. This is a lost optimistic-concurrency race, not a
 * programming error, and it is surfaced rather than swallowed so no caller assumes a transition that
 * never happened (master-design sections 6.1 and 8).
 */
public class TransitionRejectedException extends RuntimeException {

    public TransitionRejectedException(UUID questionId, QuestionState from, QuestionState to, long expectedVersion) {
        super("question " + questionId + " transition " + from + " -> " + to
                + " rejected: not in " + from + " at version " + expectedVersion);
    }
}
