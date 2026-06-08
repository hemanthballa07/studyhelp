package com.platform.payments.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.payments.domain.EarningsRepository;
import com.platform.payments.event.EarningAccrued;
import com.platform.shared.outbox.OutboxStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentsServiceTest {

    @Mock EarningsRepository earnings;
    @Mock OutboxStore outbox;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void accrue_returns_1_appendsEarningAccruedToOutbox() {
        PaymentsService service = new PaymentsService(earnings, outbox, objectMapper, fixedClock);
        UUID sourceEventId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID expertId = UUID.randomUUID();

        when(earnings.accrue(sourceEventId, questionId, expertId, 500)).thenReturn(1);

        service.accrueEarning(sourceEventId, questionId, expertId, 500);

        verify(outbox).append(argThat(e -> EarningAccrued.TYPE.equals(e.eventType())));
    }

    @Test
    void accrue_returns_0_skipsOutboxAppend() {
        PaymentsService service = new PaymentsService(earnings, outbox, objectMapper, fixedClock);
        UUID sourceEventId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID expertId = UUID.randomUUID();

        when(earnings.accrue(sourceEventId, questionId, expertId, 500)).thenReturn(0);

        service.accrueEarning(sourceEventId, questionId, expertId, 500);

        verify(outbox, never()).append(any());
    }
}
