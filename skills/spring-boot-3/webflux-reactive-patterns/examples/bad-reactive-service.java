Mono<Order> loadOrder(UUID id) {
    Order order = repository.findById(id).block();
    return Mono.just(order);
}
