@KafkaListener(topics = "orders.created")
void consume(OrderEntity event) {
    orders.save(event);
    emailClient.sendConfirmation(event.getCustomerEmail());
}
