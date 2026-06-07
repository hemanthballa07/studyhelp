package com.platform.lifecycle.event;

import java.util.UUID;

/** Domain event payload: a student posted a new question. */
public record QuestionPosted(UUID questionId, UUID studentId, String subject, String title) {

    /** Wire type, used as the {@code outbox.event_type} and {@code question_events.event_type}. */
    public static final String TYPE = "QuestionPosted";
}
