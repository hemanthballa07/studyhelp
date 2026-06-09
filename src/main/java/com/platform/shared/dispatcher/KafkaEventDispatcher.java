package com.platform.shared.dispatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.shared.outbox.OutboxEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-backed {@link EventDispatcher}. Publishes each outbox event to {@value #TOPIC} and
 * blocks until the broker ACKs ({@code acks=all}) before returning, so the relay can safely call
 * {@code markPublished}. Delivery to handlers is async — handled by {@link KafkaEventConsumer}.
 */
@Component
@Profile("kafka")
public class KafkaEventDispatcher implements EventDispatcher {

    static final String TOPIC = "platform.events";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public KafkaEventDispatcher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @Override
    public void dispatch(OutboxEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize event " + event.eventId(), ex);
        }
        try {
            // 30-second bound keeps the relay's @Scheduled thread from blocking indefinitely when the
            // broker is unreachable; spring.kafka.producer.delivery-timeout-ms should be <= 30 000.
            kafka.send(TOPIC, event.aggregateId().toString(), payload).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka send interrupted for event " + event.eventId(), ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Kafka send timed out for event " + event.eventId(), ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Kafka send failed for event " + event.eventId(), ex.getCause());
        }
    }
}
