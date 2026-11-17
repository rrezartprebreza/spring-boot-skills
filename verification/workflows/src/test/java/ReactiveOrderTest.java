import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

class ReactiveOrderTest {
    @Test void returnsExistingOrder() {
        var id = UUID.randomUUID();
        var service = new OrderQueryService(key -> Mono.just(new Order(key)));
        StepVerifier.create(service.loadOrder(id))
            .expectNext(new Order(id)).expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test void emptyResultIsNotFoundRatherThanSuccessfulEmptyResponse() {
        var service = new OrderQueryService(key -> Mono.empty());
        StepVerifier.create(service.loadOrder(UUID.randomUUID()))
            .expectError(OrderNotFound.class).verify(Duration.ofSeconds(5));
    }

    @Test void timeoutCancelsUpstreamWithoutWaitingInRealTime() {
        var cancelled = new AtomicBoolean();
        StepVerifier.withVirtualTime(() -> new OrderQueryService(
                key -> Mono.<Order>never().doOnCancel(() -> cancelled.set(true)))
            .loadOrder(UUID.randomUUID()))
            .expectSubscription().thenAwait(Duration.ofSeconds(2))
            .expectError(TimeoutException.class).verify(Duration.ofSeconds(5));
        assertTrue(cancelled.get(), "the timed-out subscription must release upstream work");
    }

    @Test void callerCancellationPropagatesToRepository() {
        var cancelled = new AtomicBoolean();
        var service = new OrderQueryService(key -> Mono.<Order>never()
            .doOnCancel(() -> cancelled.set(true)));
        StepVerifier.create(service.loadOrder(UUID.randomUUID()))
            .thenCancel().verify(Duration.ofSeconds(5));
        assertTrue(cancelled.get());
    }

    @Test void repositoryFailureIsNotReclassifiedAsMissingOrder() {
        var failure = new IllegalStateException("database unavailable");
        var service = new OrderQueryService(key -> Mono.error(failure));
        StepVerifier.create(service.loadOrder(UUID.randomUUID()))
            .expectErrorMatches(error -> error == failure).verify(Duration.ofSeconds(5));
    }
}
