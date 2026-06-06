package com.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.identity.app.RegistrationService;
import com.platform.identity.app.SubscriptionService;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.User;
import com.platform.identity.event.EntitlementChanged;
import com.platform.support.PostgresContainerSupport;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@SpringBootTest
class EntitlementEventsIT extends PostgresContainerSupport {

    @Autowired
    RegistrationService registration;

    @Autowired
    SubscriptionService subscription;

    @Autowired
    RecordingConsumer consumer;

    @Test
    void activatingProEmitsEntitlementChangedConsumedByListener() {
        User user = registration.register("ev@x.com", "password123", Role.STUDENT);

        subscription.activate(user.getId(), Plan.PRO);

        assertThat(consumer.received).anySatisfy(event -> {
            assertThat(event.userId()).isEqualTo(user.getId());
            assertThat(event.allowedFeatures()).contains("AI_ANSWER");
        });
    }

    @TestConfiguration
    static class Config {
        @Bean
        RecordingConsumer recordingConsumer() {
            return new RecordingConsumer();
        }
    }

    /** A genuine in-process consumer of the domain event. */
    static class RecordingConsumer {
        final List<EntitlementChanged> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(EntitlementChanged event) {
            received.add(event);
        }
    }
}
