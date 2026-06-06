package com.platform.identity.event;

import java.util.Set;
import java.util.UUID;

/** Domain event: the set of features a user is entitled to changed. */
public record EntitlementChanged(UUID userId, Set<String> allowedFeatures) {
}
