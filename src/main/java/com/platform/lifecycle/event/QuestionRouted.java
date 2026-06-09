package com.platform.lifecycle.event;

import java.util.UUID;

/**
 * Domain event: a question cleared dedup and was routed, so it is now available to claim. The expert
 * portal consumes this to build its queue (Slice 5). Title and body are included so the AI handler
 * can trigger generation without a separate DB read (Slice 15). Existing consumers that read only
 * specific JSON fields are backward-compatible with the extra fields.
 */
public record QuestionRouted(UUID questionId, String subject, String title, String body) {

    /** Wire type, used as the {@code outbox.event_type} and {@code question_events.event_type}. */
    public static final String TYPE = "QuestionRouted";
}
