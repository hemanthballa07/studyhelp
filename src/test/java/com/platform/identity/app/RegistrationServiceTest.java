package com.platform.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.identity.domain.Role;
import com.platform.identity.domain.Subscription;
import com.platform.identity.domain.SubscriptionRepository;
import com.platform.identity.domain.User;
import com.platform.identity.domain.UserRepository;
import com.platform.identity.event.UserRegistered;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock UserRepository users;
    @Mock SubscriptionRepository subscriptions;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher events;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, subscriptions, passwordEncoder, events,
                Clock.fixed(Instant.parse("2026-06-06T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registersHashesPasswordAndPublishesEvent() {
        when(users.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.register("a@b.com", "password123", Role.STUDENT);

        assertThat(result.getEmail()).isEqualTo("a@b.com");
        assertThat(result.getPasswordHash()).isEqualTo("HASHED");
        assertThat(result.getRole()).isEqualTo(Role.STUDENT);
        verify(subscriptions).save(any(Subscription.class));
        verify(events).publishEvent(any(UserRegistered.class));
    }

    @Test
    void rejectsDuplicateEmail() {
        when(users.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("a@b.com", "password123", Role.STUDENT))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(users, never()).save(any());
    }
}
