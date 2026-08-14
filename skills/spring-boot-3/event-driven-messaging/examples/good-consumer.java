import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class OrderCreatedConsumer {

    private final TransactionTemplate transactionTemplate;
    private final ProcessedEvents processedEvents;
    private final Orders orders;

    OrderCreatedConsumer(
            TransactionTemplate transactionTemplate,
            ProcessedEvents processedEvents,
            Orders orders) {
        this.transactionTemplate = transactionTemplate;
        this.processedEvents = processedEvents;
        this.orders = orders;
    }

    @KafkaListener(topics = "orders.created", groupId = "order-projections")
    void consume(OrderCreated event) {
        transactionTemplate.executeWithoutResult(status -> {
            if (processedEvents.markIfNew(event.eventId())) {
                orders.apply(event);
            }
        });
    }
}

record OrderCreated(UUID eventId, UUID orderId, int schemaVersion) { }

interface ProcessedEvents {
    boolean markIfNew(UUID eventId);
}

interface Orders {
    void apply(OrderCreated event);
}
