package com.platform.lifecycle.event;

import java.util.UUID;

/** Emitted by lifecycle when QC fails and the question moves to REJECTED (Slice 7). */
public record QuestionRejected(UUID questionId) {

    public static final String TYPE = "QuestionRejected";
}
