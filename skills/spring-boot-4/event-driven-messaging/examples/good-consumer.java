@KafkaListener(topics = "orders.created")
void consume(OrderCreated event) {
    transactionTemplate.executeWithoutResult(status -> {
        if (processedEvents.markIfNew(event.eventId())) {
            orders.apply(event);
        }
    });
}
