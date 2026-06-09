package com.platform.ai.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.app.AiDecisionService;
import com.platform.ai.app.DecisionOutcome;
import com.platform.ai.app.GenerationService;
import com.platform.ai.app.JudgeSampler;
import com.platform.ai.app.VerificationService;
import com.platform.ai.domain.CorpusRepository;
import com.platform.ai.domain.VerificationResult;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.integrity.IntegrityDecision;
import com.platform.shared.integrity.IntegrityPort;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.telemetry.PipelineMetrics;
import io.micrometer.core.instrument.Timer;
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
    private final PipelineMetrics pipelineMetrics;
    private final IntegrityPort integrityPort;
    private final JudgeSampler judgeSampler;

    public QuestionRoutedToAiHandler(
            CorpusRepository repo,
            GenerationService generationService,
            VerificationService verificationService,
            AiDecisionService decisionService,
            ObjectMapper objectMapper,
            PipelineMetrics pipelineMetrics,
            IntegrityPort integrityPort,
            JudgeSampler judgeSampler) {
        this.repo = repo;
        this.generationService = generationService;
        this.verificationService = verificationService;
        this.decisionService = decisionService;
        this.objectMapper = objectMapper;
        this.pipelineMetrics = pipelineMetrics;
        this.integrityPort = integrityPort;
        this.judgeSampler = judgeSampler;
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
        Timer.Sample pipeline = pipelineMetrics.startPipeline();
        try {
            JsonNode node = objectMapper.readTree(event.payload());
            UUID questionId = UUID.fromString(node.get("questionId").asText());
            String subject = node.get("subject").asText();
            String title = node.get("title").asText();
            String body = node.get("body").asText();
            String questionText = title + " " + body;

            IntegrityDecision integrity = integrityPort.assess(questionId, questionText);
            if (integrity.mode() == IntegrityDecision.Mode.REFUSE) {
                log.info("Question {} refused by integrity check", questionId);
                return;
            }
            String effectiveText = integrity.mode() == IntegrityDecision.Mode.PEDAGOGICAL
                    ? questionText + " " + integrity.promptSuffix()
                    : questionText;

            repo.recordAnswerRequest(questionId);
            log.debug("AI pipeline triggered for question {}", questionId);

            CandidateAnswer candidate = generationService.generate(questionId, effectiveText);
            VerificationResult result = verificationService.verify(
                    questionId, effectiveText, subject, candidate);
            DecisionOutcome outcome = decisionService.decide(questionId, result, subject);
            if (outcome != DecisionOutcome.ABSTAINED) {
                judgeSampler.sampleIfSelected(questionId, candidate);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to handle QuestionRouted event " + event.eventId(), ex);
        } finally {
            pipelineMetrics.stopPipeline(pipeline);
        }
    }
}
