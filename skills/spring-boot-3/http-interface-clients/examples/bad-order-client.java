@Component
@HttpExchange("https://orders.internal/orders")
interface OrderClient {
    @GetExchange("/{id}") OrderDto get(@PathVariable UUID id);
}
