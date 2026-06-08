package com.platform.qc.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.qc.domain.QcReviewRepository;
import com.platform.qc.domain.QcStatus;
import com.platform.qc.domain.RubricScore;
import com.platform.qc.event.QcFailed;
import com.platform.qc.event.QcPassed;
import com.platform.qc.event.RevisionRequested;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scores an answer, persists the review row, and emits exactly one QC outbox event
 * (QcPassed, QcFailed, or RevisionRequested) in the same transaction (master-design §7).
 */
@Service
public class QcService {

    private static final String AGGREGATE_TYPE = "Answer";

    private final QcRubricScorer scorer;
    private final QcReviewRepository reviews;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QcService(QcRubricScorer scorer, QcReviewRepository reviews, OutboxStore outbox,
            ObjectMapper objectMapper, Clock clock) {
        this.scorer = scorer;
        this.reviews = reviews;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void evaluate(UUID answerId, UUID questionId, UUID expertId, String body, String subjectCode) {
        RubricScore rubricScore = scorer.score(body, subjectCode);
        UUID reviewId = UUID.randomUUID();
        reviews.insert(reviewId, answerId, questionId, expertId, rubricScore);

        UUID eventId = UUID.randomUUID();
        String eventType;
        String payload;

        if (rubricScore.status() == QcStatus.PASS) {
            eventType = QcPassed.TYPE;
            payload = toJson(new QcPassed(answerId, questionId, expertId));
        } else if (rubricScore.status() == QcStatus.FAIL) {
            eventType = QcFailed.TYPE;
            payload = toJson(new QcFailed(answerId, questionId, expertId));
        } else {
            eventType = RevisionRequested.TYPE;
            payload = toJson(new RevisionRequested(answerId, questionId, expertId, rubricScore.suggestions()));
        }

        outbox.append(new OutboxEvent(eventId, answerId, AGGREGATE_TYPE, eventType, payload, clock.instant()));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize QC event payload", ex);
        }
    }
}
