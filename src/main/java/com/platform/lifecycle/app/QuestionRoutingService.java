package com.platform.lifecycle.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.lifecycle.domain.QuestionSnapshot;
import com.platform.lifecycle.domain.QuestionState;
import com.platform.lifecycle.event.QuestionRouted;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives a freshly posted question to the claimable pool: POSTED -> DEDUP_CHECKING -> ROUTED ->
 * CLAIMABLE (master-design 6.1). Each step is a version-guarded transition; the ROUTED step emits
 * {@code QuestionRouted} so the expert portal can queue the question (Slice 5). Dedup is a pass-through
 * stub until Slice 9 adds search, so this assumes no duplicate. It runs inside the consumer's
 * transaction, so the whole drive commits atomically with the consumer's idempotency mark.
 */
@Service
public class QuestionRoutingService {

    private static final Logger log = LoggerFactory.getLogger(QuestionRoutingService.class);

    private final QuestionRepository questions;
    private final LifecycleTransitionService transitions;
    private final ObjectMapper objectMapper;

    public QuestionRoutingService(
            QuestionRepository questions, LifecycleTransitionService transitions, ObjectMapper objectMapper) {
        this.questions = questions;
        this.transitions = transitions;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void route(UUID questionId, String subject) {
        QuestionSnapshot snapshot = questions.find(questionId)
                .orElseThrow(() -> new IllegalStateException(
                        "QuestionPosted for unknown question " + questionId + "; data inconsistency"));
        QuestionState current = snapshot.state();
        if (current != QuestionState.POSTED) {
            // Redelivery is already gated by processed_events, so reaching here is unexpected; the
            // question is already routed, so this stays a no-op but is logged loud enough to notice.
            log.warn("question {} already past POSTED (now {}); routing is a no-op", questionId, current);
            return;
        }

        long version = snapshot.version();
        version = transitions.transition(questionId, QuestionState.POSTED, QuestionState.DEDUP_CHECKING,
                version, "DedupCheckStarted", "{}", false);
        // Dedup is a pass-through until Slice 9: no duplicate found, so the question proceeds to routing.
        version = transitions.transition(questionId, QuestionState.DEDUP_CHECKING, QuestionState.ROUTED,
                version, QuestionRouted.TYPE, routedPayload(questionId, subject), true);
        transitions.transition(questionId, QuestionState.ROUTED, QuestionState.CLAIMABLE,
                version, "QuestionOpenedForClaim", "{}", false);
    }

    private String routedPayload(UUID questionId, String subject) {
        try {
            return objectMapper.writeValueAsString(new QuestionRouted(questionId, subject));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize QuestionRouted payload", ex);
        }
    }
}
