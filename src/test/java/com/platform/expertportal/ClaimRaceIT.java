package com.platform.expertportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.shared.claim.ClaimPort;
import com.platform.shared.claim.ClaimedQuestion;
import com.platform.support.PostgresContainerSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adversarial concurrency test for the atomic claim (master-design 6.2, the correctness-critical
 * property of Slice 5). Many experts race to claim from a small pool of CLAIMABLE questions in one
 * subject through {@link ClaimPort}; the Postgres {@code FOR UPDATE SKIP LOCKED} claim must hand each
 * question to exactly one expert. We assert exactly {@code min(claimers, questions)} winners, each on
 * a distinct question (zero double-assignment), every claimed row leaving the CLAIMABLE pool with a
 * single non-null claimer that matches what the port returned.
 *
 * <p>Each test uses a subject unique to itself so its claimable pool and state counts are isolated
 * from every other test sharing the singleton Postgres container. Claimer count stays at or below the
 * default Hikari pool (10); each concurrent claim holds one connection for its short transaction.
 */
@SpringBootTest
class ClaimRaceIT extends PostgresContainerSupport {

    private static final int CLAIMERS = 8;

    @Autowired
    ClaimPort claimPort;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void exactlyMinClaimersAndQuestionsWinWithNoDoubleAssignment() throws Exception {
        String subject = uniqueSubject();
        int questions = 3;
        List<UUID> claimable = insertClaimableQuestions(subject, questions);

        List<ClaimedQuestion> winners = raceForClaims(subject, CLAIMERS);

        int expectedWinners = Math.min(CLAIMERS, questions);
        assertThat(winners).as("exactly min(claimers, questions) claimers win").hasSize(expectedWinners);

        Set<UUID> wonQuestionIds = winners.stream().map(ClaimedQuestion::questionId).collect(Collectors.toSet());
        assertThat(wonQuestionIds).as("no question is handed to two experts").hasSize(winners.size());
        assertThat(wonQuestionIds).as("winners come from the seeded claimable pool").isSubsetOf(claimable);

        assertThat(countInState(subject, "CLAIMED")).as("each winner's question is now CLAIMED")
                .isEqualTo(expectedWinners);
        assertThat(countInState(subject, "CLAIMABLE")).as("claimed questions left the claimable pool")
                .isEqualTo(questions - expectedWinners);

        for (ClaimedQuestion winner : winners) {
            UUID claimedBy = jdbc.queryForObject(
                    "SELECT claimed_by FROM questions WHERE id = ?", UUID.class, winner.questionId());
            assertThat(claimedBy).as("the claimed row has a single claimer matching the returned claim")
                    .isEqualTo(winner.claimedBy());
        }
    }

    @Test
    void exactlyOneWinnerWhenManyRaceForOneQuestion() throws Exception {
        String subject = uniqueSubject();
        insertClaimableQuestions(subject, 1);

        List<ClaimedQuestion> winners = raceForClaims(subject, CLAIMERS);

        assertThat(winners).as("a single question yields a single winner").hasSize(1);
        assertThat(countInState(subject, "CLAIMED")).isEqualTo(1);
        assertThat(countInState(subject, "CLAIMABLE")).isZero();
    }

    private List<ClaimedQuestion> raceForClaims(String subject, int claimers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(claimers);
        CountDownLatch ready = new CountDownLatch(claimers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Optional<ClaimedQuestion>>> attempts = new ArrayList<>();
        for (int i = 0; i < claimers; i++) {
            UUID expertId = UUID.randomUUID();
            attempts.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return claimPort.claim(expertId, subject);
            }));
        }

        ready.await();
        go.countDown();

        List<ClaimedQuestion> winners = new ArrayList<>();
        for (Future<Optional<ClaimedQuestion>> attempt : attempts) {
            attempt.get().ifPresent(winners::add);
        }
        pool.shutdown();
        return winners;
    }

    private List<UUID> insertClaimableQuestions(String subject, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO questions (id, student_id, subject, title, body, state, deadline_at)"
                            + " VALUES (?, ?, ?, 'title', 'body', 'CLAIMABLE', ?)",
                    id, UUID.randomUUID(), subject, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
            ids.add(id);
        }
        return ids;
    }

    private int countInState(String subject, String state) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM questions WHERE subject = ? AND state = CAST(? AS question_state)",
                Integer.class, subject, state);
        return count == null ? 0 : count;
    }

    private static String uniqueSubject() {
        return "race-" + UUID.randomUUID();
    }
}
