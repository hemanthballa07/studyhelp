package com.platform.expertportal.event;

import java.util.UUID;

/**
 * Domain event: an expert submitted an answer for a question whose claim was still valid
 * (master-design 4). Emitted only for a non-stale submit, so a late/ghost submission never triggers a
 * downstream effect. QC consumes this in Slice 7.
 */
public record AnswerSubmitted(UUID answerId, UUID questionId, UUID expertId) {

    /** Wire type, used as the {@code outbox.event_type}. */
    public static final String TYPE = "AnswerSubmitted";
}
