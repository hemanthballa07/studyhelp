package com.platform.identity.app;

import com.platform.identity.domain.Feature;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Subscription;
import com.platform.identity.domain.SubscriptionRepository;
import com.platform.identity.domain.User;
import com.platform.identity.domain.UserRepository;
import com.platform.identity.event.EntitlementChanged;
import com.platform.identity.event.SubscriptionActivated;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final EntitlementService entitlements;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, UserRepository users,
                               EntitlementService entitlements, ApplicationEventPublisher events,
                               Clock clock) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.entitlements = entitlements;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Activate a user's subscription on a plan. Stands in for the effect of a (simulated) successful
     * payment until the PaymentSucceeded consumer is wired through the dispatcher in a later slice.
     */
    @Transactional
    public void activate(UUID userId, Plan plan) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
        Subscription sub = subscriptions.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
        sub.activate(plan, clock.instant());
        subscriptions.save(sub);

        events.publishEvent(new SubscriptionActivated(userId, plan.name()));
        Set<String> allowed = entitlements.entitlementsFor(user.getRole(), sub.getPlan(), sub.getStatus())
                .stream().map(Feature::name).collect(Collectors.toUnmodifiableSet());
        events.publishEvent(new EntitlementChanged(userId, allowed));
    }
}
