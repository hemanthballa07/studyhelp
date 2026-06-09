package com.platform.expertportal.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.expertportal.domain.ClaimAttemptRepository;
import com.platform.expertportal.domain.ExpertSubjectRepository;
import com.platform.expertportal.event.ExpertClaimAttempted;
import com.platform.expertportal.event.QuestionSkipped;
import com.platform.shared.claim.ClaimPort;
import com.platform.shared.claim.ClaimedQuestion;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates an expert's claim attempt. In one transaction it checks eligibility, performs the
 * atomic claim through the lifecycle-owned {@link ClaimPort} (lifecycle is the only writer of question
 * state and emits {@code QuestionClaimed} itself), records the attempt, and emits the expert-portal
 * events: {@code ExpertClaimAttempted} on every attempt and {@code QuestionSkipped} when nothing was
 * claimable. Because {@code ClaimPort.claim} joins this transaction, the question state change, the
 * attempt row, and all events commit together or not at all.
 */
@Service
public class ExpertClaimService {

    private static final String ATTEMPT_AGGREGATE = "ClaimAttempt";

    private final ClaimPort claimPort;
    private final ExpertSubjectRepository subjects;
    private final ClaimAttemptRepository attempts;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter claimWon;
    private final Counter claimSkipped;

    public ExpertClaimService(ClaimPort claimPort, ExpertSubjectRepository subjects,
            ClaimAttemptRepository attempts, OutboxStore outbox, ObjectMapper objectMapper,
            Clock clock, MeterRegistry meterRegistry) {
        this.claimPort = claimPort;
        this.subjects = subjects;
        this.attempts = attempts;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.claimWon = Counter.builder("expertportal.claim.count").tag("outcome", "WON").register(meterRegistry);
        this.claimSkipped = Counter.builder("expertportal.claim.count").tag("outcome", "SKIPPED").register(meterRegistry);
    }

    @Transactional
    public Optional<ClaimedQuestion> claim(UUID expertId, String subject) {
        if (!subjects.handles(expertId, subject)) {
            throw new NotEligibleForSubjectException(expertId, subject);
        }

        Optional<ClaimedQuestion> claimed = claimPort.claim(expertId, subject);

        UUID attemptId = UUID.randomUUID();
        ClaimOutcome outcome = claimed.isPresent() ? ClaimOutcome.WON : ClaimOutcome.SKIPPED;
        (outcome == ClaimOutcome.WON ? claimWon : claimSkipped).increment();
        UUID questionId = claimed.map(ClaimedQuestion::questionId).orElse(null);
        attempts.record(attemptId, expertId, subject, outcome.name(), questionId);

        emit(attemptId, ExpertClaimAttempted.TYPE,
                new ExpertClaimAttempted(attemptId, expertId, subject, outcome.name(), questionId));
        if (claimed.isEmpty()) {
            emit(attemptId, QuestionSkipped.TYPE, new QuestionSkipped(expertId, subject));
        }
        return claimed;
    }

    private void emit(UUID aggregateId, String eventType, Object payload) {
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), aggregateId, ATTEMPT_AGGREGATE, eventType, toJson(payload), clock.instant()));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize expert-portal event payload", ex);
        }
    }
}
