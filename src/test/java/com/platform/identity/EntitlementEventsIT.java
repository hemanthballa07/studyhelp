package com.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.identity.app.RegistrationService;
import com.platform.identity.app.SubscriptionService;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.User;
import com.platform.support.PostgresContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Activating a subscription writes EntitlementChanged to the transactional outbox in the same
 * transaction as the subscription change (master-design section 5). Lifecycle's consumption of that
 * event is covered by EntitlementProjectionIT.
 */
@SpringBootTest
class EntitlementEventsIT extends PostgresContainerSupport {

    @Autowired
    RegistrationService registration;

    @Autowired
    SubscriptionService subscription;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void activatingProWritesEntitlementChangedToTheOutbox() {
        User user = registration.register("ent-" + UUID.randomUUID() + "@x.com", "password123", Role.STUDENT);

        subscription.activate(user.getId(), Plan.PRO);

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND aggregate_type = 'User'"
                        + " AND event_type = 'EntitlementChanged'",
                Integer.class, user.getId());
        assertThat(rows).isEqualTo(1);

        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM outbox WHERE aggregate_id = ? AND event_type = 'EntitlementChanged'",
                String.class, user.getId());
        assertThat(payload).contains("AI_ANSWER");
    }
}
