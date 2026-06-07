package com.platform.shared.outbox;

import com.platform.shared.dispatcher.EventDispatcher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drains the outbox: reads a batch of unpublished events and, for each, dispatches it in its own
 * transaction then marks it published. Failures are isolated per event (logged, left unpublished for
 * the next pass) so one bad event cannot block the queue. Dispatch is idempotent, so a crash before
 * {@code markPublished} simply redelivers on the next pass (at-least-once). This is the producer side
 * of the Slice 11 Kafka swap; the relay shape is unchanged, only {@link EventDispatcher} is swapped.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxStore outbox;
    private final EventDispatcher dispatcher;

    public OutboxRelay(OutboxStore outbox, EventDispatcher dispatcher) {
        this.outbox = outbox;
        this.dispatcher = dispatcher;
    }

    /**
     * Dispatch and publish the next batch of pending events.
     *
     * @return the number of events successfully published in this pass
     */
    public int relayPending() {
        List<OutboxEvent> batch = outbox.fetchUnpublished(BATCH_SIZE);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                dispatcher.dispatch(event);
                outbox.markPublished(event.eventId());
                published++;
            } catch (RuntimeException ex) {
                log.error("relay failed for event {} type {}; leaving it unpublished for the next pass",
                        event.eventId(), event.eventType(), ex);
            }
        }
        return published;
    }
}
