package com.platform.lifecycle.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.platform.lifecycle.domain.IllegalTransitionException;
import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.lifecycle.domain.QuestionState;
import com.platform.lifecycle.domain.TransitionRejectedException;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration contract for one transition: reject illegal edges before any write, reject a lost
 * version guard explicitly, and on success append exactly one audit row plus (when emitting) one
 * outbox row that shares the audit row's event id. The real Postgres atomicity and the concurrent
 * race are proven in the Testcontainers ITs on CI.
 */
@ExtendWith(MockitoExtension.class)
class LifecycleTransitionServiceTest {

    @Mock
    QuestionRepository questions;

    @Mock
    OutboxStore outbox;

    private final UUID questionId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-07T12:00:00Z"), ZoneOffset.UTC);

    private LifecycleTransitionService service;

    @BeforeEach
    void setUp() {
        service = new LifecycleTransitionService(questions, outbox, clock);
    }

    @Test
    void illegalTransitionThrowsBeforeAnyWrite() {
        assertThatThrownBy(() -> service.transition(
                questionId, QuestionState.POSTED, QuestionState.CLAIMABLE, 0L, "X", "{}", false))
                .isInstanceOf(IllegalTransitionException.class);

        verifyNoInteractions(questions, outbox);
    }

    @Test
    void lostVersionGuardThrowsAndWritesNothing() {
        when(questions.applyTransition(questionId, QuestionState.POSTED, QuestionState.DEDUP_CHECKING, 0L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(
                questionId, QuestionState.POSTED, QuestionState.DEDUP_CHECKING, 0L, "DedupCheckStarted", "{}", false))
                .isInstanceOf(TransitionRejectedException.class);

        verify(questions, never()).appendEvent(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void appliedInternalTransitionAppendsAuditRowAndReturnsNewVersionWithoutEmitting() {
        when(questions.applyTransition(questionId, QuestionState.POSTED, QuestionState.DEDUP_CHECKING, 0L))
                .thenReturn(Optional.of(1L));

        long newVersion = service.transition(
                questionId, QuestionState.POSTED, QuestionState.DEDUP_CHECKING, 0L, "DedupCheckStarted", "{}", false);

        assertThat(newVersion).isEqualTo(1L);
        verify(questions).appendEvent(
                any(UUID.class), eq(questionId), eq("DedupCheckStarted"), eq("POSTED"), eq("DEDUP_CHECKING"), eq("{}"));
        verifyNoInteractions(outbox);
    }

    @Test
    void emittingTransitionWritesOutboxAndAuditRowSharingOneEventId() {
        when(questions.applyTransition(questionId, QuestionState.DEDUP_CHECKING, QuestionState.ROUTED, 1L))
                .thenReturn(Optional.of(2L));
        String payload = "{\"questionId\":\"" + questionId + "\",\"subject\":\"math\"}";

        long newVersion = service.transition(
                questionId, QuestionState.DEDUP_CHECKING, QuestionState.ROUTED, 1L, "QuestionRouted", payload, true);

        assertThat(newVersion).isEqualTo(2L);

        ArgumentCaptor<UUID> auditEventId = ArgumentCaptor.forClass(UUID.class);
        verify(questions).appendEvent(
                auditEventId.capture(), eq(questionId), eq("QuestionRouted"),
                eq("DEDUP_CHECKING"), eq("ROUTED"), eq(payload));

        ArgumentCaptor<OutboxEvent> outboxEvent = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).append(outboxEvent.capture());
        OutboxEvent published = outboxEvent.getValue();
        assertThat(published.eventId()).isEqualTo(auditEventId.getValue());
        assertThat(published.aggregateId()).isEqualTo(questionId);
        assertThat(published.aggregateType()).isEqualTo("Question");
        assertThat(published.eventType()).isEqualTo("QuestionRouted");
        assertThat(published.payload()).isEqualTo(payload);
        assertThat(published.occurredAt()).isEqualTo(Instant.parse("2026-06-07T12:00:00Z"));
    }
}
