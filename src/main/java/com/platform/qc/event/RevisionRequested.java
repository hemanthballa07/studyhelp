package com.platform.qc.event;

import java.util.List;
import java.util.UUID;

/** QC score in revision band (60–74). Lifecycle consumes to transition IN_REVIEW → REVISION_REQUESTED. */
public record RevisionRequested(UUID answerId, UUID questionId, UUID expertId, List<String> suggestions) {

    public static final String TYPE = "RevisionRequested";
}
