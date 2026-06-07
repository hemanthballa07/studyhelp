package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.lifecycle.app.LifecycleTransitionService;
import com.platform.lifecycle.domain.QuestionState;
import com.platform.lifecycle.domain.TransitionRejectedException;
import com.platform.support.PostgresContainerSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * Adversarial concurrency test for the optimistic-concurrency version guard (master-design 6.1, 8).
 * Several threads attempt the same transition from the same (state, version): exactly one wins, the
 * rest are rejected with no effect, and the version advances exactly once. This is the correctness-
 * critical guarantee that a question cannot transition twice under contention.
 */
@SpringBootTest
class QuestionTransitionVersionGuardIT extends PostgresContainerSupport {

    private static final int CONTENDERS = 8;

    @Autowired
    LifecycleTransitionService transitions;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void exactlyOneConcurrentTransitionWinsFromTheSameVersion() throws Exception {
        UUID questionId = insertPostedQuestion();

        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CountDownLatch ready = new CountDownLatch(CONTENDERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            attempts.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    transitions.transition(questionId, QuestionState.POSTED, QuestionState.DEDUP_CHECKING,
                            0L, "DedupCheckStarted", "{}", false);
                    return true;
                } catch (TransitionRejectedException rejected) {
                    return false;
                }
            }));
        }

        ready.await();
        go.countDown();

        int winners = 0;
        for (Future<Boolean> attempt : attempts) {
            if (attempt.get()) {
                winners++;
            }
        }
        pool.shutdown();

        assertThat(winners).as("exactly one transition may win the race").isEqualTo(1);

        Long version = jdbc.queryForObject("SELECT version FROM questions WHERE id = ?", Long.class, questionId);
        assertThat(version).as("version advances exactly once").isEqualTo(1L);

        String state = jdbc.queryForObject("SELECT state::text FROM questions WHERE id = ?", String.class, questionId);
        assertThat(state).isEqualTo("DEDUP_CHECKING");

        Integer auditRows = jdbc.queryForObject(
                "SELECT count(*) FROM question_events WHERE question_id = ? AND to_state = 'DEDUP_CHECKING'",
                Integer.class, questionId);
        assertThat(auditRows).as("only the winner appends an audit row").isEqualTo(1);
    }

    private UUID insertPostedQuestion() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at)"
                        + " VALUES (?, ?, 'math', 'title', 'body', 'POSTED', ?)",
                id, UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        return id;
    }
}
