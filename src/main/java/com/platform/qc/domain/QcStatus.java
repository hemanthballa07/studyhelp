package com.platform.qc.domain;

/** The result band of a rubric review (master-design §7). Stored as TEXT in qc_reviews.status. */
public enum QcStatus {
    PASS,
    FAIL,
    REVISION_REQUESTED
}
