package com.platform.ai.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.domain.CorpusRepository;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records an answer_request row when a question is routed so the AI pipeline
 * knows it has been dispatched. Retrieval and generation happen in Slice 13.
 * Idempotency is enforced by the ON CONFLICT DO NOTHING in recordAnswerRequest.
 */
@Component
public class QuestionRoutedToAiHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(QuestionRoutedToAiHandler.class);
    static final String CONSUMER = "ai-question-routed";

    private final CorpusRepository repo;
    private final ObjectMapper objectMapper;

    public QuestionRoutedToAiHandler(CorpusRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerName() {
        return CONSUMER;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (!"QuestionRouted".equals(event.eventType())) {
            return;
        }
        try {
            var node = objectMapper.readTree(event.payload());
            UUID questionId = UUID.fromString(node.get("questionId").asText());
            repo.recordAnswerRequest(questionId);
            log.debug("AI answer request recorded for question {}", questionId);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to handle QuestionRouted event " + event.eventId(), ex);
        }
    }
}
