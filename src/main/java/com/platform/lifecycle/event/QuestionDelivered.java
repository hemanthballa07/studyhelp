package com.platform.lifecycle.event;

import java.util.UUID;

/** Emitted by lifecycle when QC passes and the question moves to DELIVERED (Slice 7). */
public record QuestionDelivered(UUID questionId) {

    public static final String TYPE = "QuestionDelivered";
}
