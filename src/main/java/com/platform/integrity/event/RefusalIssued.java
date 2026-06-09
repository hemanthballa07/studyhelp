package com.platform.integrity.event;

import java.time.Instant;
import java.util.UUID;

/** Emitted when an integrity check triggers a full refusal rather than pedagogical mode-switch. */
public record RefusalIssued(UUID questionId, String reason, Instant issuedAt) {

    public static final String TYPE = "RefusalIssued";
}
