package com.platform.lifecycle.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.lifecycle.domain.StudentEntitlementRepository;
import com.platform.lifecycle.event.QuestionPosted;
import com.platform.shared.dispatcher.EventHandler;
import com.platform.shared.outbox.OutboxEvent;
import org.springframework.stereotype.Component;

/**
 * Lifecycle's consumer of dispatched domain events: the first real cross-context consumer on the
 * Slice 3 outbox + dispatcher. The dispatcher records idempotency in {@code processed_events} keyed
 * on this consumer name, so a redelivered event is a no-op before {@link #handle} runs; the routing
 * drive is additionally guarded by question version and the entitlement upsert is idempotent. Event
 * types lifecycle does not consume are ignored.
 */
@Component
public class LifecycleEventHandler implements EventHandler {

    private static final String CONSUMER = "lifecycle";
    // Must match identity's EntitlementChanged.TYPE wire string. The two are deliberately not coupled
    // by import (cross-context boundary); a drift on either side breaks EntitlementProjectionIT and
    // EntitlementEventsIT, which both pin this literal.
    private static final String ENTITLEMENT_CHANGED = "EntitlementChanged";

    private final QuestionRoutingService routing;
    private final StudentEntitlementRepository entitlements;
    private final ObjectMapper objectMapper;

    public LifecycleEventHandler(
            QuestionRoutingService routing, StudentEntitlementRepository entitlements, ObjectMapper objectMapper) {
        this.routing = routing;
        this.entitlements = entitlements;
        this.objectMapper = objectMapper;
    }

    @Override
    public String consumerName() {
        return CONSUMER;
    }

    @Override
    public void handle(OutboxEvent event) {
        switch (event.eventType()) {
            case QuestionPosted.TYPE -> routing.route(event.aggregateId(), textField(event, "subject"));
            case ENTITLEMENT_CHANGED -> entitlements.upsert(event.aggregateId(), arrayField(event, "allowedFeatures"));
            default -> {
                // Event types lifecycle does not consume (including QuestionRouted, which it emits
                // itself) are intentionally ignored.
            }
        }
    }

    private JsonNode payload(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.payload());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("malformed payload on event " + event.eventId(), ex);
        }
    }

    private String textField(OutboxEvent event, String field) {
        JsonNode node = payload(event).get(field);
        if (node == null || node.isNull()) {
            throw new IllegalStateException("event " + event.eventId() + ": required field '" + field + "' missing");
        }
        return node.asText();
    }

    private String arrayField(OutboxEvent event, String field) {
        JsonNode node = payload(event).get(field);
        if (node == null || !node.isArray()) {
            // Fail loud rather than overwrite the projection with an empty array on a malformed event.
            throw new IllegalStateException(
                    "event " + event.eventId() + ": field '" + field + "' missing or not a JSON array");
        }
        return node.toString();
    }
}
