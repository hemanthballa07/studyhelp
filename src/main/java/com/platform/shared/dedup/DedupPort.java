package com.platform.shared.dedup;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for duplicate detection. Placed in {@code shared} so {@code lifecycle} can call it without
 * a compile-time dependency on {@code search}. Spring injects the {@code search} implementation at
 * runtime (same pattern as {@code ClaimPort}/{@code SubmitPort}).
 */
public interface DedupPort {
    Optional<UUID> checkDuplicate(UUID questionId, String subject, String title, String body);
}
