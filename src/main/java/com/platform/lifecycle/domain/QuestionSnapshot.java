package com.platform.lifecycle.domain;

/**
 * A question's current canonical state and optimistic-concurrency version, read before driving a
 * transition so the caller can guard the conditional UPDATE with the version it observed.
 */
public record QuestionSnapshot(QuestionState state, long version) {
}
