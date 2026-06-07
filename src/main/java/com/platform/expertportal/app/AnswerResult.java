package com.platform.expertportal.app;

import java.util.UUID;

/** Outcome of submitting an answer: the persisted answer id and whether it was stale (late). */
public record AnswerResult(UUID answerId, boolean stale) {
}
