package com.platform.lifecycle.domain;

/**
 * A question's current canonical state, optimistic-concurrency version, and subject, read before
 * driving a transition or returning a SubmitResult. Subject is included so callers can propagate
 * it to cross-context events without an extra query.
 */
public record QuestionSnapshot(QuestionState state, long version, String subject) {
}
