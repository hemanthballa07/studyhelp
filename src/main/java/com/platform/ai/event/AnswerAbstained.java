package com.platform.ai.event;

import java.util.UUID;

/** Emitted when the AI verifier abstains; routes the question back to the human expert queue. */
public record AnswerAbstained(UUID questionId, double confidence, String subject) {

    public static final String TYPE = "AnswerAbstained";
}
