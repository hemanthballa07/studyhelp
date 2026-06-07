package com.platform.identity.app;

import com.platform.identity.domain.Feature;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Subscription;
import com.platform.identity.domain.SubscriptionRepository;
import com.platform.identity.domain.User;
import com.platform.identity.domain.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.identity.event.EntitlementChanged;
import com.platform.identity.event.SubscriptionActivated;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private static final String AGGREGATE_TYPE = "User";

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final EntitlementService entitlements;
    private final ApplicationEventPublisher events;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, UserRepository users,
                               EntitlementService entitlements, ApplicationEventPublisher events,
                               OutboxStore outbox, ObjectMapper objectMapper, Clock clock) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.entitlements = entitlements;
        this.events = events;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Activate a user's subscription on a plan. Stands in for the effect of a (simulated) successful
     * payment until the PaymentSucceeded consumer is wired through the dispatcher in a later slice.
     *
     * <p>EntitlementChanged is written to the transactional outbox in this same transaction, so the
     * subscription change and its published event commit together; lifecycle consumes it to keep its
     * entitlement projection current (master-design section 5).
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
        outbox.append(new OutboxEvent(UUID.randomUUID(), userId, AGGREGATE_TYPE,
                EntitlementChanged.TYPE, toJson(new EntitlementChanged(userId, allowed)), clock.instant()));
    }

    private String toJson(EntitlementChanged event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize EntitlementChanged payload", ex);
        }
    }
}
