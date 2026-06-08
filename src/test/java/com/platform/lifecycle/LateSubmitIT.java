package com.platform.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.shared.claim.SubmitOutcome;
import com.platform.shared.claim.SubmitPort;
import com.platform.shared.claim.SubmitResult;
import com.platform.support.PostgresContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adversarial test for the conditional submit and start (master-design 6.3, correctness-critical):
 * no ghost deliveries. A submit only succeeds while the caller owns the claim and the lease is live;
 * otherwise it is STALE and the question state is untouched (the answer-stale flag and the suppressed
 * payout-triggering event live in the expert portal, tested there).
 */
@SpringBootTest
class LateSubmitIT extends PostgresContainerSupport {

    @Autowired
    SubmitPort submitPort;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void submitUnderAValidLeaseTransitionsToSubmitted() {
        UUID expert = UUID.randomUUID();
        UUID questionId = seed("IN_PROGRESS", expert, true);

        SubmitResult result = submitPort.submit(expert, questionId);

        assertThat(result.outcome()).isEqualTo(SubmitOutcome.SUBMITTED);
        assertThat(stateOf(questionId)).isEqualTo("SUBMITTED");
    }

    @Test
    void submitAfterLeaseExpiryIsStaleAndLeavesStateUnchanged() {
        UUID expert = UUID.randomUUID();
        UUID questionId = seed("IN_PROGRESS", expert, false);

        SubmitResult result = submitPort.submit(expert, questionId);

        assertThat(result.outcome()).as("a ghost delivery after lease expiry is blocked").isEqualTo(SubmitOutcome.STALE);
        assertThat(stateOf(questionId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void submitByANonOwnerIsStale() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID questionId = seed("IN_PROGRESS", owner, true);

        SubmitResult result = submitPort.submit(other, questionId);

        assertThat(result.outcome()).isEqualTo(SubmitOutcome.STALE);
        assertThat(stateOf(questionId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void startMovesAClaimedQuestionToInProgressForTheOwner() {
        UUID expert = UUID.randomUUID();
        UUID questionId = seed("CLAIMED", expert, true);

        boolean started = submitPort.start(expert, questionId);

        assertThat(started).isTrue();
        assertThat(stateOf(questionId)).isEqualTo("IN_PROGRESS");
    }

    private UUID seed(String state, UUID expertId, boolean leaseValid) {
        UUID id = UUID.randomUUID();
        String leaseExpr = leaseValid ? "now() + interval '20 minutes'" : "now() - interval '1 minute'";
        jdbc.update("INSERT INTO questions"
                        + " (id, student_id, subject, title, body, state, claimed_by, claimed_at,"
                        + "  claim_expires_at, deadline_at)"
                        + " VALUES (?, ?, ?, 'title', 'body', CAST(? AS question_state), ?, now(),"
                        + "  " + leaseExpr + ", now() + interval '1 day')",
                id, UUID.randomUUID(), "submit-" + UUID.randomUUID(), state, expertId);
        return id;
    }

    private String stateOf(UUID id) {
        return jdbc.queryForObject("SELECT state::text FROM questions WHERE id = ?", String.class, id);
    }
}
