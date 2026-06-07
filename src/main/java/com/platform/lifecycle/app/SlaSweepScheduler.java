package com.platform.lifecycle.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Triggers the SLA sweep on a fixed delay in a running application, re-opening expired claim leases
 * (master-design 6.4). Disabled in tests via {@code platform.sweep.enabled=false} (set in the shared
 * Testcontainers base) so {@code SlaSweepIT} drives {@link SlaSweepService#sweep(int)} explicitly and
 * its assertions stay deterministic. Mirrors the outbox relay scheduler; safe to run on every instance
 * because the sweep itself is idempotent and {@code SKIP LOCKED} (no leader election).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "platform.sweep.enabled", havingValue = "true", matchIfMissing = true)
class SlaSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaSweepScheduler.class);
    private static final int BATCH = 100;

    private final SlaSweepService sweep;

    SlaSweepScheduler(SlaSweepService sweep) {
        this.sweep = sweep;
    }

    @Scheduled(fixedDelayString = "${platform.sweep.poll-ms:5000}")
    void sweep() {
        try {
            int reopened = sweep.sweep(BATCH);
            if (reopened > 0) {
                log.info("SLA sweep re-opened {} expired lease(s)", reopened);
            }
        } catch (RuntimeException ex) {
            // The scheduler's default handler also logs and reschedules; this adds a domain-level signal
            // and keeps a single failing tick from looking like a silent stall.
            log.error("SLA sweep tick failed; retrying after the fixed delay", ex);
        }
    }
}
