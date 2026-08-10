@HttpExchange("/orders")
interface OrderClient {
    @GetExchange("/{id}") OrderDto get(@PathVariable UUID id);
}

// Register once with HttpServiceProxyFactory and a configured RestClientAdapter.
