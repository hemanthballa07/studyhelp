package com.platform.identity.app;

import com.platform.identity.domain.Feature;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.SubscriptionStatus;

/**
 * Pure, deterministic entitlement policy: given a user's role and subscription state, decide whether
 * a feature is allowed. No I/O, so it is unit-testable in isolation.
 */
public class EntitlementPolicy {

    public boolean allows(Role role, Plan plan, SubscriptionStatus status, Feature feature) {
        boolean proActive = plan == Plan.PRO && status == SubscriptionStatus.ACTIVE;
        return switch (feature) {
            case POST_QUESTION -> role == Role.STUDENT;
            case AI_ANSWER -> role == Role.STUDENT && proActive;
            case CLAIM_QUESTION -> role == Role.EXPERT;
            case ADMIN_CONSOLE -> role == Role.ADMIN;
        };
    }
}
