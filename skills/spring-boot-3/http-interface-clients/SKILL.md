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

## Transport failure contract

Define an adapter policy before adding retry annotations. The companion
[CatalogHttpClient](templates/CatalogHttpClient.java) implements the
`CatalogRetryService.CatalogTransport` port from the resilience-retry skill and uses
explicit JDK connect/read timeouts. It is a small imperative adapter example, not a reason
to replace an existing HTTP interface or Boot-managed client.

| Failure | Example policy for an idempotent read |
| --- | --- |
| 401/403, malformed request, missing resource | Surface the domain-appropriate error; no blind retry |
| 502/503/504, connect/read timeout | Translate to the bounded retry policy |
| 429 with Retry-After | Schedule only within the caller's budget; not implemented by this example |
| Cancellation or interruption | Propagate immediately, preserving interruption |
| Invalid response body or TLS/certificate error | Diagnose; do not repeatedly retry by default |

For POST/payment calls, preserve the same idempotency key and request bytes across retries;
a timeout does not prove the server did nothing. Never forward inbound Authorization to an
arbitrary destination: configure credentials per trusted service. Do not allow an end user
to choose the base URL. Avoid logging tokens or raw upstream error bodies.

Test the real client adapter against a stub HTTP server, including permanent status codes,
transient-then-success responses, malformed bodies, delayed responses and cancellation.
Keep connect, read, pool-acquisition and total deadlines distinct. When using WebClient,
apply a total timeout around the whole retry sequence and avoid blocking the event loop.

See [Spring REST clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html).

## Verification scope

The repository fixture targets Boot 3.5.16 with Java 17/21 and checks
the imperative catalog adapter against a local HTTP server; not every declarative group-registration option. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas

- Agent uses `@ImportHttpServices` - that Boot 4 registration API is not available in Boot 3.
- Agent adds `@Component` or an implementation to the interface - register the generated proxy as a bean.
- Agent uses `RestClient` for `Mono` or `Flux` - use `WebClientAdapter` for reactive return types.
- Agent hard-codes remote hosts in annotations - keep URLs in configuration.
- Agent lets transport errors leak through the domain - translate them in the client adapter.
- Agent creates a new client per request - configure and reuse a singleton proxy.
