package com.platform.integrity.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.integrity.event.QuestionFlagged;
import com.platform.integrity.event.RefusalIssued;
import com.platform.shared.integrity.IntegrityDecision;
import com.platform.shared.integrity.IntegrityDecision.Mode;
import com.platform.shared.integrity.IntegrityPort;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keyword-based exam/live-assessment prompt classifier (§10.5).
 * >=2 keyword matches → PEDAGOGICAL mode (hints, not a submission-ready answer).
 * >=4 keyword matches → REFUSE (full refusal; human review required).
 */
@Service
public class IntegrityService implements IntegrityPort {

    static final String PEDAGOGICAL_SUFFIX =
            "Do not provide a submission-ready answer. Explain the concept with hints and a worked example only.";

    private static final List<String> EXAM_KEYWORDS = List.of(
            "due in", "due by", "deadline", "quiz", "exam",
            "test tomorrow", "submission", "homework due", "assignment due",
            "my professor", "my instructor", "for my class");

    private static final int PEDAGOGICAL_THRESHOLD = 2;
    private static final int REFUSE_THRESHOLD = 4;

    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;

    public IntegrityService(OutboxStore outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public IntegrityDecision assess(UUID questionId, String questionText) {
        int matchCount = countKeywordMatches(questionText.toLowerCase());

        if (matchCount >= REFUSE_THRESHOLD) {
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), questionId, "Integrity",
                    RefusalIssued.TYPE,
                    toJson(new RefusalIssued(questionId, "exam-like prompt detected", Instant.now())),
                    Instant.now()));
            return new IntegrityDecision(Mode.REFUSE, "");
        }

        if (matchCount >= PEDAGOGICAL_THRESHOLD) {
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), questionId, "Integrity",
                    QuestionFlagged.TYPE,
                    toJson(new QuestionFlagged(questionId, "exam-like prompt detected", Instant.now())),
                    Instant.now()));
            return new IntegrityDecision(Mode.PEDAGOGICAL, PEDAGOGICAL_SUFFIX);
        }

        return new IntegrityDecision(Mode.NORMAL, "");
    }

    private int countKeywordMatches(String lowerText) {
        int count = 0;
        for (String keyword : EXAM_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise integrity event", ex);
        }
    }
}
