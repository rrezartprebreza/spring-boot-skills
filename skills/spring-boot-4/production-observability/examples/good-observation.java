import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

final class OrderObservation {

    private final ObservationRegistry registry;

    OrderObservation(ObservationRegistry registry) {
        this.registry = registry;
    }

    void createOrder(String channel, Runnable operation) {
        Observation.createNotStarted("orders.create", registry)
            .lowCardinalityKeyValue("channel", channel)
            .observe(operation);
    }
}
