package com.platform.identity.event;

import java.util.UUID;

/** Domain event: a user's subscription became active on a plan. */
public record SubscriptionActivated(UUID userId, String plan) {
}
