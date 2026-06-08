package com.platform.shared.claim;

import java.util.UUID;

/**
 * Synchronous port for the expert work transitions that only lifecycle may apply (master-design 3):
 * starting work (CLAIMED -> IN_PROGRESS) and submitting (IN_PROGRESS -> SUBMITTED). The expert portal
 * owns the answer artifact and the endpoints, and drives these state writes through this port so it
 * keeps no compile dependency on lifecycle (ArchUnit allows depending only on {@code ..shared..}).
 */
public interface SubmitPort {

    /**
     * Move a claimed question to IN_PROGRESS, guarded by ownership and a live lease.
     *
     * @return true if the caller held the claim and it transitioned; false otherwise
     */
    boolean start(UUID expertId, UUID questionId);

    /**
     * Conditionally submit (master-design 6.3): IN_PROGRESS -> SUBMITTED only while the caller owns the
     * claim and the lease is still valid. {@link SubmitOutcome#STALE} when the guard matches no row.
     */
    SubmitOutcome submit(UUID expertId, UUID questionId);
}
