package com.platform.ai.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.app.AiDecisionService;
import com.platform.ai.app.DecisionOutcome;
import com.platform.ai.app.GenerationService;
import com.platform.ai.app.VerificationService;
import com.platform.ai.domain.CorpusRepository;
import com.platform.ai.domain.VerificationResult;
import com.platform.shared.generation.CandidateAnswer;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.telemetry.PipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionRoutedToAiHandlerTest {

    private final CorpusRepository repo = mock(CorpusRepository.class);
    private final GenerationService generationService = mock(GenerationService.class);
    private final VerificationService verificationService = mock(VerificationService.class);
    private final AiDecisionService decisionService = mock(AiDecisionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestionRoutedToAiHandler handler =
            new QuestionRoutedToAiHandler(repo, generationService, verificationService, decisionService, objectMapper,
                    new PipelineMetrics(new SimpleMeterRegistry()));

    @Test
    void handlesQuestionRoutedEvent_triggersFullPipeline() throws Exception {
        UUID questionId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of(
                "questionId", questionId.toString(),
                "subject", "Physics",
                "title", "Newton second law",
                "body", "Explain with examples."));
        CandidateAnswer candidate = new CandidateAnswer(List.of());
        VerificationResult result = new VerificationResult(
                UUID.randomUUID(), questionId, 0.8, 0.8, 0.8, 0.8, 0.8);
        when(generationService.generate(eq(questionId), anyString())).thenReturn(candidate);
        when(verificationService.verify(eq(questionId), anyString(), eq("Physics"), eq(candidate)))
                .thenReturn(result);
        when(decisionService.decide(eq(questionId), eq(result), eq("Physics")))
                .thenReturn(DecisionOutcome.PRODUCED);

        handler.handle(new OutboxEvent(
                UUID.randomUUID(), questionId, "Question", "QuestionRouted", payload, Instant.now()));

        verify(repo).recordAnswerRequest(questionId);
        verify(generationService).generate(eq(questionId), anyString());
        verify(verificationService).verify(eq(questionId), anyString(), eq("Physics"), eq(candidate));
        verify(decisionService).decide(eq(questionId), eq(result), eq("Physics"));
    }

    @Test
    void ignoresOtherEventTypes() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Question", "QuestionPosted",
                "{\"questionId\":\"" + UUID.randomUUID() + "\"}", Instant.now());

        handler.handle(event);

        verify(repo, never()).recordAnswerRequest(any());
        verify(generationService, never()).generate(any(), anyString());
    }

    @Test
    void consumerNameIsStable() {
        assertThat(handler.consumerName()).isEqualTo(QuestionRoutedToAiHandler.CONSUMER);
    }
}
