package com.platform.expertportal.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.expertportal.domain.AnswerRepository;
import com.platform.expertportal.event.AnswerSubmitted;
import com.platform.shared.claim.SubmitOutcome;
import com.platform.shared.claim.SubmitPort;
import com.platform.shared.claim.SubmitResult;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submits an expert answer. In one transaction it drives the conditional question transition through
 * the lifecycle-owned {@link SubmitPort} (master-design 6.3), persists the answer, and emits
 * {@code AnswerSubmitted} only when the submit was valid. A stale submit (lease expired, wrong owner,
 * wrong state) still persists the answer flagged stale but emits nothing payout-triggering, so there
 * is no ghost delivery. Because {@code SubmitPort.submit} joins this transaction, the state write and
 * the answer row commit together or not at all.
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private static final String AGGREGATE_TYPE = "Answer";

    private final SubmitPort submitPort;
    private final AnswerRepository answers;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnswerService(SubmitPort submitPort, AnswerRepository answers, OutboxStore outbox,
            ObjectMapper objectMapper, Clock clock) {
        this.submitPort = submitPort;
        this.answers = answers;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AnswerResult submit(UUID expertId, UUID questionId, String body) {
        SubmitResult result = submitPort.submit(expertId, questionId);
        boolean stale = result.outcome() == SubmitOutcome.STALE;
        UUID answerId = UUID.randomUUID();
        answers.insert(answerId, questionId, expertId, body, stale);
        if (stale) {
            // No payout-triggering event for a late/ghost submission; surface it so a spike (e.g. after
            // a DB outage expired many leases) is visible rather than silently swallowed.
            log.warn("stale answer submission: expertId={} questionId={} answerId={} (lease expired or not owner)",
                    expertId, questionId, answerId);
        } else {
            outbox.append(new OutboxEvent(UUID.randomUUID(), answerId, AGGREGATE_TYPE, AnswerSubmitted.TYPE,
                    toJson(new AnswerSubmitted(answerId, questionId, expertId, body, result.subjectCode())),
                    clock.instant()));
        }
        return new AnswerResult(answerId, stale);
    }

    private String toJson(AnswerSubmitted event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize AnswerSubmitted payload", ex);
        }
    }
}
