Mono<Order> loadOrder(UUID id) {
    return repository.findById(id)
        .switchIfEmpty(Mono.error(new OrderNotFound(id)))
        .timeout(Duration.ofSeconds(2));
}
