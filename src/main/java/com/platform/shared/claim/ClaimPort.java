package com.platform.shared.claim;

import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous port for claiming the next available question. Lifecycle owns question state and is the
 * sole implementer (master-design 3); other contexts (the expert portal) initiate a claim through this
 * port rather than touching the {@code questions} table, which keeps them free of a compile dependency
 * on lifecycle (the ArchUnit boundary allows depending only on {@code ..shared..}). The atomic claim
 * itself is master-design 6.2 (Postgres {@code FOR UPDATE SKIP LOCKED}).
 */
public interface ClaimPort {

    /**
     * Atomically claim the next claimable question for {@code subject} on behalf of {@code expertId}.
     *
     * @return the claimed question, or empty when nothing is currently claimable for that subject
     */
    Optional<ClaimedQuestion> claim(UUID expertId, String subject);
}
