package com.platform.shared.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Triggers the outbox relay on a fixed delay in a running application, so events actually drain to
 * their consumers. Disabled in tests via {@code platform.outbox.relay.enabled=false} (set in the
 * shared Testcontainers base) so integration tests drive {@link OutboxRelay#relayPending()}
 * explicitly and assertions on unpublished rows stay deterministic. When the dispatcher is swapped
 * for Kafka in Slice 11, this trigger is unchanged.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "platform.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler {

    private final OutboxRelay relay;

    OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.relay.poll-ms:1000}")
    void drain() {
        relay.relayPending();
    }
}
