package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.shared.dispatcher.EventDispatcher;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lifecycle consumes EntitlementChanged and upserts its student_entitlements projection; redelivery
 * leaves exactly one row (master-design 3, 5). This proves the full identity -> outbox -> dispatcher
 * -> lifecycle cross-context flow on the consumer side.
 */
@SpringBootTest
class EntitlementProjectionIT extends PostgresContainerSupport {

    @Autowired
    EventDispatcher dispatcher;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void consumingEntitlementChangedUpsertsProjectionAndReplayIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, userId, "User", "EntitlementChanged",
                "{\"userId\":\"" + userId + "\",\"allowedFeatures\":[\"AI_ANSWER\",\"POST_QUESTION\"]}",
                Instant.now());

        dispatcher.dispatch(event);
        dispatcher.dispatch(event);

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM student_entitlements WHERE user_id = ?", Integer.class, userId);
        assertThat(rows).isEqualTo(1);

        String features = jdbc.queryForObject(
                "SELECT allowed_features::text FROM student_entitlements WHERE user_id = ?", String.class, userId);
        assertThat(features).contains("AI_ANSWER").contains("POST_QUESTION");

        Integer processed = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer = 'lifecycle' AND event_id = ?",
                Integer.class, eventId);
        assertThat(processed).isEqualTo(1);
    }
}
