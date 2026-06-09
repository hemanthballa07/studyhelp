package com.platform.integrity.event;

import java.time.Instant;
import java.util.UUID;

/** Emitted when a question's assessed risk level transitions (skeleton — wired in a later slice). */
public record RiskLevelChanged(UUID questionId, String previousLevel, String newLevel, Instant changedAt) {

    public static final String TYPE = "RiskLevelChanged";
}
