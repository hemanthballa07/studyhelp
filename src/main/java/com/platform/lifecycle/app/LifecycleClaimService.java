package com.platform.lifecycle.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.lifecycle.domain.ClaimedRow;
import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.lifecycle.event.QuestionClaimed;
import com.platform.shared.claim.ClaimPort;
import com.platform.shared.claim.ClaimedQuestion;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle's implementation of {@link ClaimPort}: the only writer of canonical question state runs
 * the master-design 6.2 {@code FOR UPDATE SKIP LOCKED} claim, appends the append-only
 * {@code question_events} audit row (CLAIMABLE -> CLAIMED), and emits {@code QuestionClaimed} on the
 * outbox, all in one transaction, so the state change and its published event commit together or not
 * at all. When nothing is claimable the call is a no-op returning empty (no audit row, no event). The
 * audit row and the outbox row share one event id.
 */
@Service
public class LifecycleClaimService implements ClaimPort {

    private static final String AGGREGATE_TYPE = "Question";
    private static final String FROM_STATE = "CLAIMABLE";
    private static final String TO_STATE = "CLAIMED";

    private final QuestionRepository questions;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int leaseMinutes;

    public LifecycleClaimService(QuestionRepository questions, OutboxStore outbox, ObjectMapper objectMapper,
            Clock clock, @Value("${platform.claim.lease-minutes:20}") int leaseMinutes) {
        this.questions = questions;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.leaseMinutes = leaseMinutes;
    }

    @Override
    @Transactional
    public Optional<ClaimedQuestion> claim(UUID expertId, String subject) {
        Optional<ClaimedRow> claimed = questions.claimNextClaimable(subject, expertId, leaseMinutes);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        ClaimedRow row = claimed.get();
        UUID eventId = UUID.randomUUID();
        String payload = toJson(new QuestionClaimed(
                row.questionId(), row.subject(), row.claimedBy(), row.claimExpiresAt()));
        questions.appendEvent(eventId, row.questionId(), QuestionClaimed.TYPE, FROM_STATE, TO_STATE, payload);
        outbox.append(new OutboxEvent(
                eventId, row.questionId(), AGGREGATE_TYPE, QuestionClaimed.TYPE, payload, clock.instant()));
        return Optional.of(new ClaimedQuestion(
                row.questionId(), row.subject(), row.claimedBy(), row.claimExpiresAt(), row.version()));
    }

    private String toJson(QuestionClaimed event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize QuestionClaimed payload", ex);
        }
    }
}
