package com.platform.integrity.event;

import java.time.Instant;
import java.util.UUID;

/** Emitted when a question is classified as a likely exam/live-assessment prompt. */
public record QuestionFlagged(UUID questionId, String reason, Instant detectedAt) {

    public static final String TYPE = "QuestionFlagged";
}
