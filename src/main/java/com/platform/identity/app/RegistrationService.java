package com.platform.identity.app;

import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.Subscription;
import com.platform.identity.domain.SubscriptionRepository;
import com.platform.identity.domain.SubscriptionStatus;
import com.platform.identity.domain.User;
import com.platform.identity.domain.UserRepository;
import com.platform.identity.event.UserRegistered;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RegistrationService(UserRepository users, SubscriptionRepository subscriptions,
                               PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                               Clock clock) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String rawPassword, Role role) {
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        Instant now = clock.instant();
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), role, now);
        users.save(user);
        // Everyone starts on FREE / INACTIVE; upgrades flow through SubscriptionService.
        subscriptions.save(new Subscription(
                UUID.randomUUID(), user.getId(), Plan.FREE, SubscriptionStatus.INACTIVE, null, now));
        events.publishEvent(new UserRegistered(user.getId(), user.getEmail(), user.getRole().name()));
        return user;
    }
}
