package com.platform.lifecycle.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.lifecycle.domain.ExpiredLease;
import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.lifecycle.event.ClaimLeaseExpired;
import com.platform.lifecycle.event.QuestionExpired;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sweeps expired claim leases (master-design 6.4). In one transaction it grabs a batch of overdue
 * leases with {@code FOR UPDATE SKIP LOCKED} (so two sweepers take disjoint rows), and for each:
 * appends the CLAIM_EXPIRED audit row, emits {@code QuestionExpired} (cross-context; carries the
 * subject so the queue projection re-adds it) and the slice-local {@code ClaimLeaseExpired}, then
 * re-opens the question CLAIM_EXPIRED -> CLAIMABLE. Every step is version-guarded and the grabbed rows
 * are locked to commit, so concurrent sweeps on multiple instances are safe with no leader election.
 */
@Service
public class SlaSweepService {

    private static final Logger log = LoggerFactory.getLogger(SlaSweepService.class);

    private static final String AGGREGATE_TYPE = "Question";
    private static final String CLAIM_EXPIRED = "CLAIM_EXPIRED";
    private static final String CLAIMABLE = "CLAIMABLE";

    private final QuestionRepository questions;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SlaSweepService(
            QuestionRepository questions, OutboxStore outbox, ObjectMapper objectMapper, Clock clock) {
        this.questions = questions;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public int sweep(int batchSize) {
        List<ExpiredLease> expired = questions.expireLeasedBatch(batchSize);
        for (ExpiredLease lease : expired) {
            log.debug("sweeping expired lease: questionId={} fromState={} claimedBy={}",
                    lease.questionId(), lease.fromState(), lease.claimedBy());

            UUID expiredEventId = UUID.randomUUID();
            String expiredPayload = toJson(
                    new QuestionExpired(lease.questionId(), lease.subject(), lease.claimedBy()));
            questions.appendEvent(expiredEventId, lease.questionId(), QuestionExpired.TYPE,
                    lease.fromState(), CLAIM_EXPIRED, expiredPayload);
            outbox.append(new OutboxEvent(expiredEventId, lease.questionId(), AGGREGATE_TYPE,
                    QuestionExpired.TYPE, expiredPayload, clock.instant()));
            outbox.append(new OutboxEvent(UUID.randomUUID(), lease.questionId(), AGGREGATE_TYPE,
                    ClaimLeaseExpired.TYPE, toJson(new ClaimLeaseExpired(lease.questionId(), lease.claimedBy())),
                    clock.instant()));

            if (!questions.reopenExpired(lease.questionId())) {
                // The row was just locked and set to CLAIM_EXPIRED in this transaction, so the reopen
                // must match. A 0-row result means a regression; fail loud to roll the whole batch back
                // rather than leave a question stuck in CLAIM_EXPIRED with a published QuestionExpired.
                throw new IllegalStateException("reopenExpired matched no CLAIM_EXPIRED row for question "
                        + lease.questionId() + "; sweep invariant violated");
            }
            questions.appendEvent(UUID.randomUUID(), lease.questionId(), "QuestionReopened",
                    CLAIM_EXPIRED, CLAIMABLE, "{}");
        }
        return expired.size();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize sweep event payload", ex);
        }
    }
}
