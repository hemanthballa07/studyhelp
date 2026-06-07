package com.platform.expertportal.web.dto;

import com.platform.expertportal.domain.ClaimableQuestionView;
import java.time.Instant;
import java.util.UUID;

/** One entry of the claimable-queue read view returned by {@code GET /api/claims/queue}. */
public record QueueItem(UUID questionId, String subject, Instant routedAt) {

    public static QueueItem from(ClaimableQuestionView view) {
        return new QueueItem(view.questionId(), view.subject(), view.routedAt());
    }
}
