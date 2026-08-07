---
name: http-interface-clients
description: >
  Use when calling external HTTP APIs from Spring Boot 3 / Spring Framework 6 with declarative
  HttpExchange interfaces. Covers manual proxy registration, RestClient versus WebClient, and
  timeout and error handling conventions.
---

# Declarative HTTP Interface Clients (Boot 3)

Spring Framework 6 supports `@HttpExchange` interfaces, but Boot 3 does not provide Boot 4's
`@ImportHttpServices` group auto-registration. Build the client adapter explicitly.

```java
@HttpExchange("/orders")
interface OrderApiClient {
    @GetExchange("/{id}")
    OrderDto get(@PathVariable UUID id);
}

@Configuration
class ClientConfig {
    @Bean
    OrderApiClient orderApiClient(RestClient.Builder builder,
                                  @Value("${clients.orders.base-url}") String baseUrl) {
        RestClient client = builder.baseUrl(baseUrl).build();
        HttpServiceProxyFactory factory =
            HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build();
        return factory.createClient(OrderApiClient.class);
    }
}
```

Use `WebClientAdapter` for reactive interfaces returning `Mono` or `Flux`. Configure base URLs,
timeouts, authentication, and error translation in the client adapter layer rather than in
controllers or domain services. Prefer one factory/configurer per external service.

## Gotchas

- Agent uses `@ImportHttpServices` - that Boot 4 registration API is not available in Boot 3.
- Agent adds `@Component` or an implementation to the interface - register the generated proxy as a bean.
- Agent uses `RestClient` for `Mono` or `Flux` - use `WebClientAdapter` for reactive return types.
- Agent hard-codes remote hosts in annotations - keep URLs in configuration.
- Agent lets transport errors leak through the domain - translate them in the client adapter.
- Agent creates a new client per request - configure and reuse a singleton proxy.
