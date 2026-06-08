package com.platform.qc.event;

import java.util.UUID;

/** QC rubric passed (score ≥ 75). Lifecycle consumes this to transition IN_REVIEW → DELIVERED. */
public record QcPassed(UUID answerId, UUID questionId, UUID expertId) {

    public static final String TYPE = "QcPassed";
}
