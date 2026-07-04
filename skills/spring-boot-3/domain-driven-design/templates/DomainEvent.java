package com.example.common.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events.
 * Events are immutable facts about something that happened in the domain.
 */
public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
}

// --- Concrete domain events as records ---

package com.example.order.domain.event;

import com.example.common.domain.DomainEvent;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(
    UUID eventId,
    Instant occurredAt,
    OrderId orderId,
    CustomerId customerId,
    Money total
) implements DomainEvent {

    public static OrderPlaced of(Order order) {
        return new OrderPlaced(
            UUID.randomUUID(),
            Instant.now(),
            order.getId(),
            order.getCustomerId(),
            order.getTotal()
        );
    }
}

public record OrderCancelled(
    UUID eventId,
    Instant occurredAt,
    OrderId orderId,
    CustomerId customerId,
    String reason
) implements DomainEvent {

    public OrderCancelled(OrderId orderId, CustomerId customerId, String reason, Instant occurredAt) {
        this(UUID.randomUUID(), occurredAt, orderId, customerId, reason);
    }
}
