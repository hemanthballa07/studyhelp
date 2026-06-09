package com.platform.integrity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.integrity.event.QuestionFlagged;
import com.platform.integrity.event.RefusalIssued;
import com.platform.shared.integrity.IntegrityDecision;
import com.platform.shared.integrity.IntegrityDecision.Mode;
import com.platform.shared.outbox.OutboxStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrityServiceTest {

    private final OutboxStore outbox = mock(OutboxStore.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private IntegrityService svc;

    @BeforeEach
    void setUp() {
        svc = new IntegrityService(outbox, objectMapper);
    }

    @Test
    void assess_withTwoKeywords_returnsPedagogical() {
        UUID qId = UUID.randomUUID();
        IntegrityDecision decision = svc.assess(qId, "I have an exam due tomorrow quiz question");

        assertThat(decision.mode()).isEqualTo(Mode.PEDAGOGICAL);
        assertThat(decision.promptSuffix()).isEqualTo(IntegrityService.PEDAGOGICAL_SUFFIX);
        verify(outbox, times(1)).append(argThat(e -> QuestionFlagged.TYPE.equals(e.eventType())));
    }

    @Test
    void assess_withFourKeywords_returnsRefuse() {
        UUID qId = UUID.randomUUID();
        // matches: "exam", "due in", "homework due", "my professor"
        IntegrityDecision decision = svc.assess(qId,
                "exam due in 2 hours homework due today my professor wants submission");

        assertThat(decision.mode()).isEqualTo(Mode.REFUSE);
        verify(outbox, times(1)).append(argThat(e -> RefusalIssued.TYPE.equals(e.eventType())));
    }

    @Test
    void assess_withNoKeywords_returnsNormal() {
        UUID qId = UUID.randomUUID();
        IntegrityDecision decision = svc.assess(qId,
                "Explain Newton second law of motion with examples of force and acceleration.");

        assertThat(decision.mode()).isEqualTo(Mode.NORMAL);
        verify(outbox, never()).append(argThat(e -> true));
    }
}
