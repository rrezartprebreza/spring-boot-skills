@Component
@HttpExchange("https://orders.internal/orders")
interface OrderClient {
    Mono<OrderDto> get(UUID id); // default group client is blocking
}
