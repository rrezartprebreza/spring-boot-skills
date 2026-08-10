final class OrderObservation {
    void record(ObservationRegistry registry, String outcome, Runnable operation) {
        Observation.createNotStarted("orders.create", registry)
            .lowCardinalityKeyValue("outcome", outcome)
            .observe(operation);
    }
}
