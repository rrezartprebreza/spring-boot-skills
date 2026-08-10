final class OrderObservation {
    void record(MeterRegistry registry, String userId, String orderId) {
        registry.counter("orders.create", "user", userId, "order", orderId).increment();
    }
}
