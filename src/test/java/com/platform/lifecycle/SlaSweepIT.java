package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.lifecycle.app.SlaSweepService;
import com.platform.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adversarial concurrency test for the SLA sweep (master-design 6.4, correctness-critical). Two
 * sweepers run with no leader election against a pool of expired CLAIMED/IN_PROGRESS leases; the
 * {@code FOR UPDATE SKIP LOCKED} grab plus version-guarded transitions must re-open each question
 * exactly once: ending CLAIMABLE, version advanced by exactly two (expire + reopen), claim fields
 * cleared, and exactly one {@code QuestionExpired} per question. A unique subject isolates this test
 * on the shared container.
 */
@SpringBootTest
class SlaSweepIT extends PostgresContainerSupport {

    private static final int BATCH = 2;

    @Autowired
    SlaSweepService sweep;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void twoConcurrentSweepersReopenEachExpiredLeaseExactlyOnce() throws Exception {
        String subject = "sweep-" + UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(seedExpired(subject, i % 2 == 0 ? "CLAIMED" : "IN_PROGRESS"));
        }

        runConcurrentSweepers(2);

        for (UUID id : ids) {
            assertThat(stateOf(id)).as("expired lease re-opened to CLAIMABLE").isEqualTo("CLAIMABLE");
            assertThat(versionOf(id)).as("expire + reopen advance version exactly twice").isEqualTo(2L);
            assertThat(claimedByOf(id)).as("claim fields cleared on reopen").isNull();
            assertThat(outboxCount(id, "QuestionExpired")).as("exactly one expiry event per question").isEqualTo(1);
        }
        assertThat(countState(subject, "CLAIMABLE")).isEqualTo(6);
        assertThat(countState(subject, "CLAIM_EXPIRED")).as("nothing stuck mid-sweep").isZero();
    }

    private void runConcurrentSweepers(int sweepers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(sweepers);
        CountDownLatch ready = new CountDownLatch(sweepers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < sweepers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                while (sweep.sweep(BATCH) > 0) {
                    // drain until no expired lease remains
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
    }

    private UUID seedExpired(String subject, String state) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questions"
                        + " (id, student_id, subject, title, body, state, claimed_by, claimed_at,"
                        + "  claim_expires_at, deadline_at)"
                        + " VALUES (?, ?, ?, 'title', 'body', CAST(? AS question_state), ?,"
                        + "  now() - interval '1 hour', now() - interval '30 minutes', now() + interval '1 day')",
                id, UUID.randomUUID(), subject, state, UUID.randomUUID());
        return id;
    }

    private String stateOf(UUID id) {
        return jdbc.queryForObject("SELECT state::text FROM questions WHERE id = ?", String.class, id);
    }

    private Long versionOf(UUID id) {
        return jdbc.queryForObject("SELECT version FROM questions WHERE id = ?", Long.class, id);
    }

    private UUID claimedByOf(UUID id) {
        return jdbc.queryForObject("SELECT claimed_by FROM questions WHERE id = ?", UUID.class, id);
    }

    private int outboxCount(UUID id, String eventType) {
        return count("SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?", id, eventType);
    }

    private int countState(String subject, String state) {
        return count("SELECT count(*) FROM questions WHERE subject = ? AND state = CAST(? AS question_state)",
                subject, state);
    }

    private int count(String sql, Object... args) {
        Integer c = jdbc.queryForObject(sql, Integer.class, args);
        return c == null ? 0 : c;
    }
}
