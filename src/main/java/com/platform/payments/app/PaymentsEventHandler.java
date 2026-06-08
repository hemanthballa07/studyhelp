package com.platform.payments.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Payments' outbox consumer. Handles QuestionDelivered events only — does not import lifecycle
 * types (cross-context ArchUnit boundary satisfied by wire string constant, same pattern as
 * QcEventHandler).
 */
@Component
public class PaymentsEventHandler implements EventHandler {

    // Wire string: must match QuestionDelivered.TYPE without importing lifecycle
    private static final String QUESTION_DELIVERED = "QuestionDelivered";
    private static final int EXPERT_AMOUNT_CENTS = 500;

    private final PaymentsService paymentsService;
    private final ObjectMapper objectMapper;

    public PaymentsEventHandler(PaymentsService paymentsService, ObjectMapper objectMapper) {
        this.paymentsService = paymentsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerName() {
        return "payments";
    }

    @Override
    public void handle(OutboxEvent event) {
        if (!QUESTION_DELIVERED.equals(event.eventType())) {
            return;
        }
        JsonNode payload = parse(event);
        UUID questionId = UUID.fromString(requireText(event, payload, "questionId"));
        UUID expertId   = UUID.fromString(requireText(event, payload, "expertId"));
        paymentsService.accrueEarning(event.eventId(), questionId, expertId, EXPERT_AMOUNT_CENTS);
    }

    private JsonNode parse(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("malformed payload on event " + event.eventId(), ex);
        }
    }

    private String requireText(OutboxEvent event, JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalStateException(
                    "event " + event.eventId() + ": required field '" + field + "' missing");
        }
        return node.asText();
    }
}
