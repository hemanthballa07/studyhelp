package com.platform.lifecycle.event;

import java.util.UUID;

/** Emitted by lifecycle when a near-duplicate question is detected during dedup check. */
public record DuplicateDetected(UUID questionId, UUID duplicateOfId) {
    public static final String TYPE = "DuplicateDetected";
}
