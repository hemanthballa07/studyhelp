package com.platform.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.payments.app.PaymentsService;
import com.platform.shared.dispatcher.EventDispatcher;
import com.platform.shared.outbox.OutboxEvent;
import com.platform.shared.outbox.OutboxStore;
import com.platform.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class EarningAccrualIdempotencyIT extends PostgresContainerSupport {

    @Autowired EventDispatcher dispatcher;
    @Autowired OutboxStore outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentsService paymentsService;

    @Test
    void dispatchingQuestionDeliveredTwiceAccruesExactlyOneEarning() {
        UUID sourceEventId = UUID.randomUUID();
        UUID questionId    = UUID.randomUUID();
        UUID expertId      = UUID.randomUUID();
        String payload = """
                {"questionId":"%s","expertId":"%s"}
                """.formatted(questionId, expertId);

        OutboxEvent event = new OutboxEvent(
                sourceEventId, questionId, "Question", "QuestionDelivered", payload, Instant.now());
        outbox.append(event);

        dispatcher.dispatch(event);   // first: handler runs, inserts earning + EarningAccrued
        dispatcher.dispatch(event);   // second: processed_events guard fires, handler skipped

        Integer earningCount = jdbc.queryForObject(
                "SELECT count(*) FROM earnings WHERE source_event_id = ?",
                Integer.class, sourceEventId);
        assertThat(earningCount).isEqualTo(1);

        Integer accruedCount = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = 'EarningAccrued' AND aggregate_id = ?",
                Integer.class, questionId);
        assertThat(accruedCount).isEqualTo(1);
    }

    @Test
    void callingAccrueTwiceWithSameSourceEventIdAccruesExactlyOnce() {
        // DB-level defense-in-depth: ON CONFLICT (source_event_id) DO NOTHING.
        // Bypasses the processed_events guard by calling the service directly.
        UUID sourceEventId = UUID.randomUUID();
        UUID questionId    = UUID.randomUUID();
        UUID expertId      = UUID.randomUUID();

        paymentsService.accrueEarning(sourceEventId, questionId, expertId, 500);
        paymentsService.accrueEarning(sourceEventId, questionId, expertId, 500);

        Integer earningCount = jdbc.queryForObject(
                "SELECT count(*) FROM earnings WHERE source_event_id = ?",
                Integer.class, sourceEventId);
        assertThat(earningCount).isEqualTo(1);

        Integer accruedCount = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = 'EarningAccrued' AND aggregate_id = ?",
                Integer.class, questionId);
        assertThat(accruedCount).isEqualTo(1);
    }
}
