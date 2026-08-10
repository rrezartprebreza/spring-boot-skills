@HttpExchange("/orders")
interface OrderClient {
    @GetExchange("/{id}") OrderDto get(@PathVariable UUID id);
}

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "orders", types = OrderClient.class)
class OrderClientConfiguration { }
