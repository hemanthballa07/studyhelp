package com.platform.lifecycle.event;

import java.util.UUID;

/**
 * Domain event: a question cleared dedup and was routed, so it is now available to claim. The expert
 * portal consumes this to build its queue (Slice 5). Routing-score detail (master-design 6.5) is
 * added once expert data exists; for now it carries the subject the queue filters on.
 */
public record QuestionRouted(UUID questionId, String subject) {

    /** Wire type, used as the {@code outbox.event_type} and {@code question_events.event_type}. */
    public static final String TYPE = "QuestionRouted";
}
