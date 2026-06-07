package com.platform.lifecycle.domain;

import java.util.UUID;

/**
 * Writes the lifecycle-owned {@code student_entitlements} projection: a read-model of each student's
 * allowed features, kept current from identity's {@code EntitlementChanged} event (master-design
 * sections 3, 4). The upsert is idempotent so a replayed event re-applies the same row.
 */
public interface StudentEntitlementRepository {

    /** Insert or replace the projection for {@code userId} with the given feature-name JSON array. */
    void upsert(UUID userId, String allowedFeaturesJson);
}
