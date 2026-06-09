package com.platform.ai.event;

import java.util.UUID;

/** Emitted when the AI verifier decides the answer meets the confidence threshold for delivery. */
public record AnswerProduced(UUID questionId, double confidence, boolean flagged) {

    public static final String TYPE = "AnswerProduced";
}
