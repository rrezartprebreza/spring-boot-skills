import java.time.Duration;
import java.util.UUID;

import reactor.core.publisher.Mono;

final class OrderQueryService {

    private final ReactiveOrderRepository repository;

    OrderQueryService(ReactiveOrderRepository repository) {
        this.repository = repository;
    }

    Mono<Order> loadOrder(UUID id) {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new OrderNotFound(id)))
            .timeout(Duration.ofSeconds(2));
    }
}

interface ReactiveOrderRepository {
    Mono<Order> findById(UUID id);
}

record Order(UUID id) { }

final class OrderNotFound extends RuntimeException {
    OrderNotFound(UUID id) {
        super("Order not found: " + id);
    }
}
