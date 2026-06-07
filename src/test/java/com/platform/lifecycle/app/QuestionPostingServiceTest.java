package com.platform.lifecycle.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration contract for posting a question: one question row, one append-only audit row, and
 * one outbox row, all describing the same aggregate and sharing one event identity. The real
 * single-transaction atomicity (rollback if any write fails) is proven against Postgres in
 * {@code QuestionPostingIT} on CI.
 */
@ExtendWith(MockitoExtension.class)
class QuestionPostingServiceTest {

    @Mock
    QuestionRepository questions;

    @Mock
    OutboxStore outbox;

    QuestionPostingService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
        service = new QuestionPostingService(questions, outbox, new ObjectMapper(), clock);
    }

    @Test
    void postWritesQuestionRowThenOutboxRowForTheSameAggregate() {
        UUID studentId = UUID.randomUUID();
        Instant deadline = Instant.parse("2026-07-01T00:00:00Z");
        PostQuestionCommand cmd =
                new PostQuestionCommand(studentId, "math", "Limit of sin x over x", "Show the steps", deadline);

        UUID questionId = service.post(cmd);

        verify(questions).insertPostedQuestion(questionId, studentId, "math", "Limit of sin x over x", "Show the steps", deadline);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).append(outboxCaptor.capture());
        OutboxEvent published = outboxCaptor.getValue();
        assertThat(published.aggregateId()).isEqualTo(questionId);
        assertThat(published.aggregateType()).isEqualTo("Question");
        assertThat(published.eventType()).isEqualTo("QuestionPosted");
        assertThat(published.occurredAt()).isEqualTo(Instant.parse("2026-06-06T12:00:00Z"));
        assertThat(published.payload())
                .contains(questionId.toString())
                .contains(studentId.toString())
                .contains("math");
    }

    @Test
    void appendOnlyEventAndOutboxRowShareOneEventIdAndPayload() {
        PostQuestionCommand cmd = new PostQuestionCommand(
                UUID.randomUUID(), "physics", "Title", "Body", Instant.parse("2026-07-01T00:00:00Z"));

        service.post(cmd);

        ArgumentCaptor<UUID> eventId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> auditPayload = ArgumentCaptor.forClass(String.class);
        verify(questions).appendEvent(
                eventId.capture(), any(UUID.class), eq("QuestionPosted"), isNull(), eq("POSTED"), auditPayload.capture());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).append(outboxCaptor.capture());

        assertThat(outboxCaptor.getValue().eventId()).isEqualTo(eventId.getValue());
        assertThat(outboxCaptor.getValue().payload()).isEqualTo(auditPayload.getValue());
    }
}
