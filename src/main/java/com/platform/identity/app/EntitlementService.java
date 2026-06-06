package com.platform.identity.app;

import com.platform.identity.domain.Feature;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.Subscription;
import com.platform.identity.domain.SubscriptionRepository;
import com.platform.identity.domain.SubscriptionStatus;
import com.platform.identity.domain.User;
import com.platform.identity.domain.UserRepository;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService {

    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final EntitlementPolicy policy = new EntitlementPolicy();

    public EntitlementService(UserRepository users, SubscriptionRepository subscriptions) {
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public boolean check(String email, Feature feature) {
        User user = users.findByEmail(email).orElseThrow(() -> new UserNotFoundException(email));
        Subscription sub = subscriptions.findByUserId(user.getId()).orElse(null);
        Plan plan = sub != null ? sub.getPlan() : Plan.FREE;
        SubscriptionStatus status = sub != null ? sub.getStatus() : SubscriptionStatus.INACTIVE;
        return policy.allows(user.getRole(), plan, status, feature);
    }

    /** The full set of features allowed for a role + subscription state. */
    public Set<Feature> entitlementsFor(Role role, Plan plan, SubscriptionStatus status) {
        return Arrays.stream(Feature.values())
                .filter(f -> policy.allows(role, plan, status, f))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Feature.class)));
    }
}
