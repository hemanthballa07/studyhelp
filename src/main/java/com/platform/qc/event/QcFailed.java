package com.platform.qc.event;

import java.util.UUID;

/** QC rubric failed (score < 60). Lifecycle consumes this to transition IN_REVIEW → REJECTED. */
public record QcFailed(UUID answerId, UUID questionId, UUID expertId) {

    public static final String TYPE = "QcFailed";
}
