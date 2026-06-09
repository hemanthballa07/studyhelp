package com.platform.expertportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.shared.claim.ClaimPort;
import com.platform.support.PostgresContainerSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Load test for the claim path (§6.6). Measures throughput and latency under sustained concurrent
 * load. Writes a text report to build/reports/load/claim-load-report.txt for CI archival.
 * Asserts zero errors and a conservative throughput floor.
 */
@SpringBootTest
class ClaimLoadIT extends PostgresContainerSupport {

    private static final int CONCURRENCY = 8;
    private static final int SEED_QUESTIONS = 100;
    private static final long DURATION_MS = 5_000;
    private static final double MIN_THROUGHPUT = 5.0;

    @Autowired ClaimPort claimPort;
    @Autowired JdbcTemplate jdbc;

    @Test
    void claimPath_sustainedConcurrentLoad_meetsThresholdWithZeroErrors() throws Exception {
        String subject = "load-" + UUID.randomUUID();
        seedClaimableQuestions(subject, SEED_QUESTIONS);

        List<Long> latenciesNs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger attempts = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch go = new CountDownLatch(1);
        Instant deadline = Instant.now().plusMillis(DURATION_MS);

        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                while (Instant.now().isBefore(deadline)) {
                    UUID expertId = UUID.randomUUID();
                    long start = System.nanoTime();
                    try {
                        attempts.incrementAndGet();
                        boolean won = claimPort.claim(expertId, subject).isPresent();
                        long ns = System.nanoTime() - start;
                        latenciesNs.add(ns);
                        if (won) successes.incrementAndGet();
                    } catch (Exception ex) {
                        errors.incrementAndGet();
                    }
                }
            });
        }

        ready.await();
        long startMs = System.currentTimeMillis();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(DURATION_MS + 5_000, TimeUnit.MILLISECONDS);
        long actualDurationMs = System.currentTimeMillis() - startMs;

        assertThat(errors.get()).as("zero errors during load test").isEqualTo(0);

        List<Long> sorted = new ArrayList<>(latenciesNs);
        Collections.sort(sorted);
        long p50ns = sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
        long p99ns = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.99));

        double throughput = successes.get() / (actualDurationMs / 1000.0);
        assertThat(throughput).as("throughput must exceed %.1f claims/sec", MIN_THROUGHPUT)
                .isGreaterThanOrEqualTo(MIN_THROUGHPUT);

        writeReport(subject, actualDurationMs, attempts.get(), successes.get(),
                errors.get(), throughput, p50ns / 1_000_000L, p99ns / 1_000_000L);
    }

    private void writeReport(String subject, long durationMs, int attempts, int successes,
            int errors, double throughput, long p50ms, long p99ms) throws IOException {
        Path reportDir = Path.of("build/reports/load");
        Files.createDirectories(reportDir);
        String report = String.format(
                "Claim Path Load Test Report%n"
                + "===========================%n"
                + "Subject:      %s%n"
                + "Duration:     %ds%n"
                + "Concurrency:  %d threads%n"
                + "Attempts:     %d%n"
                + "Successes:    %d%n"
                + "Errors:       %d%n"
                + "Throughput:   %.1f claims/sec%n"
                + "Latency p50:  %d ms%n"
                + "Latency p99:  %d ms%n",
                subject, durationMs / 1000, CONCURRENCY,
                attempts, successes, errors, throughput, p50ms, p99ms);
        Files.writeString(reportDir.resolve("claim-load-report.txt"), report);
    }

    private void seedClaimableQuestions(String subject, int count) {
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at)"
                    + " VALUES (?, ?, ?, 'title', 'body', 'CLAIMABLE', ?)",
                    id, UUID.randomUUID(), subject,
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        }
    }
}
