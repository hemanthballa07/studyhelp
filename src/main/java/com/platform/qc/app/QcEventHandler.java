package com.platform.qc.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * QC's outbox consumer. Handles AnswerSubmitted events only — does not import expertportal types
 * (cross-context ArchUnit boundary is satisfied by reading the wire string constant, not an import).
 */
@Component
public class QcEventHandler implements EventHandler {

    // Wire string: must match AnswerSubmitted.TYPE without importing expertportal
    private static final String ANSWER_SUBMITTED = "AnswerSubmitted";

    private final QcService qcService;
    private final ObjectMapper objectMapper;

    public QcEventHandler(QcService qcService, ObjectMapper objectMapper) {
        this.qcService = qcService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerName() {
        return "qc";
    }

    @Override
    public void handle(OutboxEvent event) {
        if (!ANSWER_SUBMITTED.equals(event.eventType())) {
            return;
        }
        JsonNode payload = parse(event);
        UUID answerId = UUID.fromString(payload.get("answerId").asText());
        UUID questionId = UUID.fromString(payload.get("questionId").asText());
        UUID expertId = UUID.fromString(payload.get("expertId").asText());
        String body = payload.get("body").asText();
        String subjectCode = payload.get("subjectCode").asText();
        qcService.evaluate(answerId, questionId, expertId, body, subjectCode);
    }

    private JsonNode parse(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("malformed payload on event " + event.eventId(), ex);
        }
    }
}
