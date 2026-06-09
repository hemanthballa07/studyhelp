package com.platform.ai.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.app.AiDecisionService;
import com.platform.ai.app.GenerationService;
import com.platform.ai.app.VerificationService;
import com.platform.ai.domain.CorpusRepository;
import com.platform.ai.domain.VerificationResult;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.outbox.OutboxEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Triggers the full AI pipeline when a question is routed. Records the answer_request, then
 * drives generation → verification → decision in sequence. Each service manages its own
 * transaction; idempotency is enforced by the dispatcher's processed_events guard and by
 * the ON CONFLICT DO NOTHING in recordAnswerRequest and VerificationService.
 */
@Component
public class QuestionRoutedToAiHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(QuestionRoutedToAiHandler.class);
    static final String CONSUMER = "ai-question-routed";

    private final CorpusRepository repo;
    private final GenerationService generationService;
    private final VerificationService verificationService;
    private final AiDecisionService decisionService;
    private final ObjectMapper objectMapper;

    public QuestionRoutedToAiHandler(
            CorpusRepository repo,
            GenerationService generationService,
            VerificationService verificationService,
            AiDecisionService decisionService,
            ObjectMapper objectMapper) {
        this.repo = repo;
        this.generationService = generationService;
        this.verificationService = verificationService;
        this.decisionService = decisionService;
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
            JsonNode node = objectMapper.readTree(event.payload());
            UUID questionId = UUID.fromString(node.get("questionId").asText());
            String subject = node.get("subject").asText();
            String title = node.get("title").asText();
            String body = node.get("body").asText();
            String questionText = title + " " + body;

            repo.recordAnswerRequest(questionId);
            log.debug("AI pipeline triggered for question {}", questionId);

            CandidateAnswer candidate = generationService.generate(questionId, questionText);
            VerificationResult result = verificationService.verify(
                    questionId, questionText, subject, candidate);
            decisionService.decide(questionId, result, subject);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to handle QuestionRouted event " + event.eventId(), ex);
        }
    }
}
