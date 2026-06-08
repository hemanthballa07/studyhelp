package com.platform.qc.domain;

import java.util.UUID;

/** Writes QC review results. QC is the only writer of qc_reviews (master-design section 3). */
public interface QcReviewRepository {

    void insert(UUID id, UUID answerId, UUID questionId, UUID expertId, RubricScore score);
}
