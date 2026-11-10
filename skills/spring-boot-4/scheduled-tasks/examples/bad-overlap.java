// BAD: illustrative fragment. Every replica fires; async returns before the sweep finishes.
@Scheduled(fixedRate = 1000)
@Async
public void chargeAllOverdueOrders() {
    repository.findAll().forEach(order -> paymentGateway.charge(order));
    // No bounded claim, tenant boundary, recovery or provider idempotency.
}
