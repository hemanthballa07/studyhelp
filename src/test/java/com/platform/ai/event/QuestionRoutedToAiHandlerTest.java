package com.platform.ai.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.domain.CorpusRepository;
import com.platform.shared.outbox.OutboxEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionRoutedToAiHandlerTest {

    private final CorpusRepository repo = mock(CorpusRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestionRoutedToAiHandler handler =
            new QuestionRoutedToAiHandler(repo, objectMapper);

    @Test
    void handlesQuestionRoutedEvent() throws Exception {
        UUID questionId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(
                Map.of("questionId", questionId.toString(), "subject", "Physics"));
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), questionId, "Question", "QuestionRouted", payload, Instant.now());

        handler.handle(event);

        verify(repo).recordAnswerRequest(questionId);
    }

    @Test
    void ignoresOtherEventTypes() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Question", "QuestionPosted",
                "{\"questionId\":\"" + UUID.randomUUID() + "\"}", Instant.now());

        handler.handle(event);

        verify(repo, never()).recordAnswerRequest(any());
    }

    @Test
    void consumerNameIsStable() {
        assertThat(handler.consumerName()).isEqualTo(QuestionRoutedToAiHandler.CONSUMER);
    }
}
