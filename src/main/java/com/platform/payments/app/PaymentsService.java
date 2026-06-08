package com.platform.payments.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.payments.domain.EarningsRepository;
import com.platform.payments.event.EarningAccrued;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentsService {

    private final EarningsRepository earnings;
    private final OutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentsService(
            EarningsRepository earnings,
            OutboxStore outbox,
            ObjectMapper objectMapper,
            Clock clock) {
        this.earnings = earnings;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void accrueEarning(UUID sourceEventId, UUID questionId, UUID expertId, int amountCents) {
        int rows = earnings.accrue(sourceEventId, questionId, expertId, amountCents);
        if (rows == 0) {
            return;
        }
        EarningAccrued payload = new EarningAccrued(questionId, expertId, amountCents);
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                questionId,
                "Question",
                EarningAccrued.TYPE,
                toJson(payload),
                clock.instant());
        outbox.append(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize payments event payload", ex);
        }
    }
}
