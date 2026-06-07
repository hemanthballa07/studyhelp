package com.platform.shared.dispatcher;

import com.platform.shared.outbox.OutboxEvent;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-process {@link EventDispatcher}: runs each registered {@link EventHandler} in the calling
 * thread. Per consumer it first claims the event in {@link ProcessedEventStore}; an already-claimed
 * event is skipped, which makes redelivery of a committed event a no-op. Each call runs in its own
 * new transaction ({@code REQUIRES_NEW}), so a handler failure rolls back only this event's claim
 * and effects (the event is retried on the next pass) and never touches sibling events when the relay
 * drains a batch.
 */
@Component
public class InProcessEventDispatcher implements EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventDispatcher.class);

    private final List<EventHandler> handlers;
    private final ProcessedEventStore processedEvents;

    public InProcessEventDispatcher(List<EventHandler> handlers, ProcessedEventStore processedEvents) {
        this.handlers = handlers;
        this.processedEvents = processedEvents;
    }

    @PostConstruct
    void logRegisteredConsumers() {
        if (handlers.isEmpty()) {
            log.warn("no EventHandler beans registered; dispatched events are marked published without delivery");
        } else {
            log.info("dispatching to {} consumer(s): {}", handlers.size(),
                    handlers.stream().map(EventHandler::consumerName).toList());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(OutboxEvent event) {
        for (EventHandler handler : handlers) {
            String consumer = handler.consumerName();
            if (!processedEvents.markProcessed(consumer, event.eventId())) {
                continue;
            }
            try {
                handler.handle(event);
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "consumer " + consumer + " failed to handle event " + event.eventId(), ex);
            }
        }
    }
}
