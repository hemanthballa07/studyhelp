package com.platform.search.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Search's outbox consumer. Indexes questions on QuestionPosted and touches the index on
 * QuestionDelivered. Does not import lifecycle types — cross-context boundary satisfied by wire
 * string constants (same pattern as PaymentsEventHandler).
 */
@Component
public class SearchEventHandler implements EventHandler {

    private static final String QUESTION_POSTED    = "QuestionPosted";
    private static final String QUESTION_DELIVERED = "QuestionDelivered";

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public SearchEventHandler(SearchService searchService, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerName() {
        return "search";
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.eventType()) {
            case QUESTION_POSTED -> {
                JsonNode payload = parse(event);
                UUID questionId = event.aggregateId();
                String subject = requireText(event, payload, "subject");
                String title   = requireText(event, payload, "title");
                String body    = requireText(event, payload, "body");
                searchService.indexQuestion(questionId, subject, title, body);
            }
            case QUESTION_DELIVERED -> searchService.touchIndexed(event.aggregateId());
            default -> { /* other event types are not consumed by search */ }
        }
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
