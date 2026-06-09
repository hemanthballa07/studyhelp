package com.platform.lifecycle.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.lifecycle.domain.QuestionSnapshot;
import com.platform.lifecycle.domain.QuestionState;
import com.platform.lifecycle.domain.StudentEntitlementRepository;
import com.platform.lifecycle.event.QuestionDelivered;
import com.platform.lifecycle.event.QuestionPosted;
import com.platform.lifecycle.event.QuestionRejected;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lifecycle's consumer of dispatched domain events. The dispatcher records idempotency in
 * {@code processed_events} keyed on this consumer name, so a redelivered event is a no-op before
 * {@link #handle} runs. Handles QuestionPosted, EntitlementChanged (Slices 3/4) and four QC-driven
 * transitions (Slice 7). Event types not listed here are intentionally ignored.
 */
@Component
public class LifecycleEventHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(LifecycleEventHandler.class);

    private static final String CONSUMER = "lifecycle";
    // Must match identity's EntitlementChanged.TYPE wire string. Cross-context boundary: no import.
    private static final String ENTITLEMENT_CHANGED = "EntitlementChanged";
    // Must match QC's wire strings. Cross-context boundary: no import of qc.* types.
    private static final String ANSWER_SUBMITTED = "AnswerSubmitted";
    private static final String QC_PASSED = "QcPassed";
    private static final String QC_FAILED = "QcFailed";
    private static final String REVISION_REQUESTED = "RevisionRequested";
    // Must match ai.event.AnswerProduced.TYPE. Cross-context boundary: no import of ai.* types.
    private static final String ANSWER_PRODUCED = "AnswerProduced";

    private final QuestionRoutingService routing;
    private final StudentEntitlementRepository entitlements;
    private final QuestionRepository questions;
    private final LifecycleTransitionService transitions;
    private final ObjectMapper objectMapper;

    public LifecycleEventHandler(
            QuestionRoutingService routing,
            StudentEntitlementRepository entitlements,
            QuestionRepository questions,
            LifecycleTransitionService transitions,
            ObjectMapper objectMapper) {
        this.routing = routing;
        this.entitlements = entitlements;
        this.questions = questions;
        this.transitions = transitions;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerName() {
        return CONSUMER;
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.eventType()) {
            case QuestionPosted.TYPE -> routing.route(
                    event.aggregateId(),
                    textField(event, "subject"),
                    textField(event, "title"),
                    textField(event, "body"));
            case ENTITLEMENT_CHANGED -> entitlements.upsert(event.aggregateId(), arrayField(event, "allowedFeatures"));
            case ANSWER_SUBMITTED -> transitionSimple(event, "questionId",
                    QuestionState.SUBMITTED, QuestionState.IN_REVIEW, "ReviewStarted", "{}", false);
            case QC_PASSED -> {
                UUID questionId = uuidField(event, "questionId");
                UUID expertId   = uuidField(event, "expertId");
                QuestionSnapshot snap = requireSnapshot(questionId, QC_PASSED);
                transitions.transition(questionId, QuestionState.IN_REVIEW, QuestionState.DELIVERED,
                        snap.version(), QuestionDelivered.TYPE, toJson(new QuestionDelivered(questionId, expertId)), true);
            }
            case QC_FAILED -> {
                UUID questionId = uuidField(event, "questionId");
                QuestionSnapshot snap = requireSnapshot(questionId, QC_FAILED);
                transitions.transition(questionId, QuestionState.IN_REVIEW, QuestionState.REJECTED,
                        snap.version(), QuestionRejected.TYPE, toJson(new QuestionRejected(questionId)), true);
            }
            case REVISION_REQUESTED -> transitionSimple(event, "questionId",
                    QuestionState.IN_REVIEW, QuestionState.REVISION_REQUESTED, "AnswerRevisionRequested", "{}", false);
            case ANSWER_PRODUCED -> {
                UUID questionId = uuidField(event, "questionId");
                QuestionSnapshot snap = requireSnapshot(questionId, ANSWER_PRODUCED);
                if (snap.state() == QuestionState.CLAIMABLE) {
                    transitions.transition(questionId, QuestionState.CLAIMABLE, QuestionState.DELIVERED,
                            snap.version(), QuestionDelivered.TYPE,
                            toJson(new QuestionDelivered(questionId, null)), true);
                } else {
                    log.info("AnswerProduced for {} but state is {}; AI answer superseded by expert work, no-op",
                            questionId, snap.state());
                }
            }
            default -> {
                // Event types lifecycle does not consume are intentionally ignored.
            }
        }
    }

    private void transitionSimple(OutboxEvent event, String questionIdField,
            QuestionState from, QuestionState to, String eventType, String payload, boolean emitToOutbox) {
        UUID questionId = uuidField(event, questionIdField);
        QuestionSnapshot snap = requireSnapshot(questionId, event.eventType());
        transitions.transition(questionId, from, to, snap.version(), eventType, payload, emitToOutbox);
    }

    private QuestionSnapshot requireSnapshot(UUID questionId, String eventType) {
        return questions.find(questionId)
                .orElseThrow(() -> new IllegalStateException(
                        eventType + " for unknown question " + questionId + "; data inconsistency"));
    }

    private UUID uuidField(OutboxEvent event, String field) {
        return UUID.fromString(textField(event, field));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize lifecycle event payload", ex);
        }
    }

    private JsonNode payload(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("malformed payload on event " + event.eventId(), ex);
        }
    }

    private String textField(OutboxEvent event, String field) {
        JsonNode node = payload(event).get(field);
        if (node == null || node.isNull()) {
            throw new IllegalStateException("event " + event.eventId() + ": required field '" + field + "' missing");
        }
        return node.asText();
    }

    private String arrayField(OutboxEvent event, String field) {
        JsonNode node = payload(event).get(field);
        if (node == null || !node.isArray()) {
            throw new IllegalStateException(
                    "event " + event.eventId() + ": field '" + field + "' missing or not a JSON array");
        }
        return node.toString();
    }
}
