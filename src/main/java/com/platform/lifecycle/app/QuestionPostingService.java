package com.platform.lifecycle.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.lifecycle.event.QuestionPosted;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts a question. In one transaction it writes the canonical question row, the append-only
 * {@code question_events} audit row, and the outbox row, so the state change and the published
 * event commit together or not at all (master-design section 5). The audit row and the outbox row
 * share one {@code eventId} and one JSON payload.
 */
@Service
public class QuestionPostingService {

    private static final String AGGREGATE_TYPE = "Question";
    private static final String POSTED = "POSTED";

    private final QuestionRepository questions;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter questionPostedCounter;

    public QuestionPostingService(
            QuestionRepository questions, OutboxStore outbox, ObjectMapper objectMapper,
            Clock clock, MeterRegistry meterRegistry) {
        this.questions = questions;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.questionPostedCounter = Counter.builder("lifecycle.question.posted")
                .description("Questions posted by students")
                .register(meterRegistry);
    }

    @Transactional
    public UUID post(PostQuestionCommand command) {
        UUID questionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        questions.insertPostedQuestion(
                questionId, command.studentId(), command.subject(), command.title(), command.body(),
                command.deadlineAt());

        String payload = toJson(new QuestionPosted(
                questionId, command.studentId(), command.subject(), command.title(), command.body()));
        questions.appendEvent(eventId, questionId, QuestionPosted.TYPE, null, POSTED, payload);
        outbox.append(new OutboxEvent(
                eventId, questionId, AGGREGATE_TYPE, QuestionPosted.TYPE, payload, clock.instant()));
        questionPostedCounter.increment();

        return questionId;
    }

    private String toJson(QuestionPosted event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize QuestionPosted payload", ex);
        }
    }
}
