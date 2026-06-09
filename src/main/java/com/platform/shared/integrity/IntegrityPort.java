package com.platform.shared.integrity;

import java.util.UUID;

/**
 * Port for exam/live-assessment prompt detection and pedagogical mode-switching (§10.5).
 * Lives in shared so ai context can depend on it without importing integrity internals.
 */
public interface IntegrityPort {
    IntegrityDecision assess(UUID questionId, String questionText);
}
