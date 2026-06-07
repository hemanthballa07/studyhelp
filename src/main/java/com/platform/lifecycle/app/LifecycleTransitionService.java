package com.platform.lifecycle.app;

import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.lifecycle.domain.QuestionState;
import com.platform.lifecycle.domain.QuestionStateMachine;
import com.platform.lifecycle.domain.TransitionRejectedException;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one question state transition (master-design sections 6.1, 5). In a single transaction it
 * checks the edge against {@link QuestionStateMachine}, runs the version-guarded conditional UPDATE,
 * appends the append-only {@code question_events} audit row, and (for a cross-context edge) writes
 * the outbox row, so the state change and its published event commit together or not at all. An
 * illegal edge is rejected before any write; a lost version guard (no row matched) is surfaced as
 * {@link TransitionRejectedException}, never silently ignored. The audit row and the outbox row
 * share one event id.
 */
@Service
public class LifecycleTransitionService {

    private static final String AGGREGATE_TYPE = "Question";

    private final QuestionRepository questions;
    private final OutboxStore outbox;
    private final Clock clock;
    private final QuestionStateMachine stateMachine = new QuestionStateMachine();

    public LifecycleTransitionService(QuestionRepository questions, OutboxStore outbox, Clock clock) {
        this.questions = questions;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Move {@code questionId} from {@code from} to {@code to}, guarded by {@code expectedVersion}.
     *
     * @param eventType    the type recorded on the audit row and (when emitting) the outbox event
     * @param payloadJson  the JSON body shared by the audit row and the outbox event
     * @param emitToOutbox whether this edge publishes a cross-context domain event
     * @return the question's new version
     * @throws com.platform.lifecycle.domain.IllegalTransitionException if the edge is not in section 6.1
     * @throws TransitionRejectedException                              if no row matched the guard
     */
    @Transactional
    public long transition(UUID questionId, QuestionState from, QuestionState to, long expectedVersion,
            String eventType, String payloadJson, boolean emitToOutbox) {
        stateMachine.assertLegal(from, to);

        long newVersion = questions.applyTransition(questionId, from, to, expectedVersion)
                .orElseThrow(() -> new TransitionRejectedException(questionId, from, to, expectedVersion));

        UUID eventId = UUID.randomUUID();
        questions.appendEvent(eventId, questionId, eventType, from.name(), to.name(), payloadJson);
        if (emitToOutbox) {
            outbox.append(new OutboxEvent(
                    eventId, questionId, AGGREGATE_TYPE, eventType, payloadJson, clock.instant()));
        }
        return newVersion;
    }
}
