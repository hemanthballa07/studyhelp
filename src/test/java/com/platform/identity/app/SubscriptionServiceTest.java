package com.platform.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.identity.domain.Feature;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.Subscription;
import com.platform.identity.domain.SubscriptionRepository;
import com.platform.identity.domain.SubscriptionStatus;
import com.platform.identity.domain.User;
import com.platform.identity.domain.UserRepository;
import com.platform.identity.event.EntitlementChanged;
import com.platform.identity.event.SubscriptionActivated;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock UserRepository users;
    @Mock EntitlementService entitlements;
    @Mock ApplicationEventPublisher events;

    private SubscriptionService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptions, users, entitlements, events,
                Clock.fixed(Instant.parse("2026-06-06T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void activatingProPublishesActivationAndEntitlementChange() {
        User user = new User(userId, "s@x.com", "h", Role.STUDENT, Instant.EPOCH);
        Subscription sub = new Subscription(UUID.randomUUID(), userId, Plan.FREE,
                SubscriptionStatus.INACTIVE, null, Instant.EPOCH);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptions.findByUserId(userId)).thenReturn(Optional.of(sub));
        when(entitlements.entitlementsFor(eq(Role.STUDENT), eq(Plan.PRO), eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(EnumSet.of(Feature.POST_QUESTION, Feature.AI_ANSWER));

        service.activate(userId, Plan.PRO);

        assertThat(sub.getPlan()).isEqualTo(Plan.PRO);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptions).save(sub);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(event ->
                assertThat(event).isInstanceOf(SubscriptionActivated.class));
        assertThat(captor.getAllValues()).anySatisfy(event -> {
            assertThat(event).isInstanceOf(EntitlementChanged.class);
            EntitlementChanged changed = (EntitlementChanged) event;
            assertThat(changed.userId()).isEqualTo(userId);
            assertThat(changed.allowedFeatures()).contains("AI_ANSWER", "POST_QUESTION");
        });
    }
}
