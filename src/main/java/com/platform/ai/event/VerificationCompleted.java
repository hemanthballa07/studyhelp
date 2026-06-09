package com.platform.ai.event;

import java.util.UUID;

/** Emitted after all 4 verifier signals have been computed and persisted (§10.3–10.4). */
public record VerificationCompleted(UUID questionId, double aggregateScore) {

    public static final String TYPE = "VerificationCompleted";
}
