package com.platform.shared.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.shared.outbox.OutboxEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kafka consumer side of the Slice 11 dispatcher swap. Subscribes to {@link KafkaEventDispatcher#TOPIC}
 * and drives all registered {@link EventHandler}s with the same per-consumer idempotency as the
 * in-process dispatcher: {@link ProcessedEventStore} records each {@code (consumer, event_id)} pair
 * so replays are no-ops. Each message is processed in an independent {@code REQUIRES_NEW} transaction
 * via {@link TransactionTemplate} — a handler failure rolls back only that event's claim and effects,
 * matching the at-least-once / retry-on-failure contract.
 */
@Component
@Profile("kafka")
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final List<EventHandler> handlers;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public KafkaEventConsumer(List<EventHandler> handlers, ProcessedEventStore processedEvents,
            ObjectMapper objectMapper, PlatformTransactionManager txManager) {
        this.handlers = handlers;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @KafkaListener(topics = KafkaEventDispatcher.TOPIC,
            groupId = "${spring.kafka.consumer.group-id:platform-dispatcher}")
    public void onEvent(String message) {
        final OutboxEvent event;
        try {
            event = objectMapper.readValue(message, OutboxEvent.class);
        } catch (Exception ex) {
            // Malformed message: log and discard. Dead-lettering deferred to Slice 19.
            log.error("Kafka message deserialization failed; discarding: {}", message, ex);
            return;
        }
        txTemplate.execute(status -> {
            for (EventHandler handler : handlers) {
                String consumer = handler.consumerName();
                if (!processedEvents.markProcessed(consumer, event.eventId())) {
                    continue;
                }
                try {
                    handler.handle(event);
                } catch (RuntimeException ex) {
                    status.setRollbackOnly();
                    // All markProcessed rows in this tx roll back, so every handler retries.
                    // After DefaultErrorHandler exhausts retries (10 by default) the offset
                    // commits and the message is dropped — Slice 19 adds a dead-letter topic.
                    throw new IllegalStateException(
                            "consumer " + consumer + " failed to handle event " + event.eventId(), ex);
                }
            }
            return null;
        });
    }
}
