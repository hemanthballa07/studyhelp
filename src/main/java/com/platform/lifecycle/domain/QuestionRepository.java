package com.platform.lifecycle.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes to the lifecycle's canonical tables. Lifecycle is the only writer of question state
 * (master-design section 3). {@code question_events} is append-only: this interface exposes inserts
 * only, with no update or delete path.
 */
public interface QuestionRepository {

    void insertPostedQuestion(
            UUID id, UUID studentId, String subject, String title, String body, Instant deadlineAt);

    void appendEvent(
            UUID eventId, UUID questionId, String eventType, String fromState, String toState, String payloadJson);
}
