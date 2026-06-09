package com.platform.expertportal.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.expertportal.domain.ClaimableQueueRepository;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Expert portal's consumer of dispatched domain events: it maintains the claimable-queue projection.
 * A question is added on {@code QuestionRouted} and removed on {@code QuestionClaimed}. Both lifecycle
 * events are matched by their wire-string type (not an import) to keep this context free of a compile
 * dependency on lifecycle (the ArchUnit boundary). The dispatcher records idempotency in
 * {@code processed_events} keyed on this consumer name, and the projection writes are themselves
 * idempotent, so a redelivered event is a no-op. Event types this context does not consume are ignored.
 */
@Component
public class ExpertPortalEventHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(ExpertPortalEventHandler.class);

    private static final String CONSUMER = "expertportal";
    private static final String QUESTION_ROUTED = "QuestionRouted";
    private static final String QUESTION_CLAIMED = "QuestionClaimed";
    private static final String QUESTION_EXPIRED = "QuestionExpired";
    // Must match ai.event.AnswerAbstained.TYPE. Cross-context boundary: no import of ai.* types.
    private static final String ANSWER_ABSTAINED = "AnswerAbstained";

    private final ClaimableQueueRepository queue;
    private final ObjectMapper objectMapper;

    public ExpertPortalEventHandler(ClaimableQueueRepository queue, ObjectMapper objectMapper) {
        this.queue = queue;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerName() {
        return CONSUMER;
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.eventType()) {
            // A routed question is claimable, and an expired claim is claimable again: both add it back.
            case QUESTION_ROUTED, QUESTION_EXPIRED -> queue.add(event.aggregateId(), textField(event, "subject"));
            case QUESTION_CLAIMED -> removeFromQueue(event.aggregateId());
            case ANSWER_ABSTAINED -> {
                // AI abstained: question was already in the queue from QuestionRouted; re-add is
                // idempotent via PRIMARY KEY. Handler makes the escalation explicit in the audit log.
                UUID questionId = event.aggregateId();
                String subject = textField(event, "subject");
                log.info("AI abstained for question {}; confirming in expert queue for subject '{}'",
                        questionId, subject);
                queue.add(questionId, subject);
            }
            default -> {
                // Event types the expert portal does not consume (including the events it emits) are
                // intentionally ignored.
            }
        }
    }

    private void removeFromQueue(UUID questionId) {
        // Redeliveries are short-circuited by processed_events before handle() runs, so a 0-row remove
        // here is not a benign replay: the question was never projected (its QuestionRouted likely
        // failed), so the read model and canonical question state have diverged. Surface it loudly.
        if (queue.remove(questionId) == 0) {
            log.warn("QuestionClaimed for {} but it was absent from the claimable queue;"
                    + " read model diverged from lifecycle state", questionId);
        }
    }

    private String textField(OutboxEvent event, String field) {
        JsonNode node = payload(event).get(field);
        if (node == null || node.isNull()) {
            throw new IllegalStateException("event " + event.eventId() + ": required field '" + field + "' missing");
        }
        return node.asText();
    }

    private JsonNode payload(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("malformed payload on event " + event.eventId(), ex);
        }
    }
}
