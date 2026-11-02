---
name: http-interface-clients
description: >
  Use when calling external HTTP APIs from Spring Boot 4 / Spring Framework 7 with declarative
  HttpExchange interfaces. Covers ImportHttpServices, grouped base URLs and timeouts, and
  RestClient versus WebClient selection.
---

# Declarative HTTP Interface Clients (Boot 4)

Define a remote API as an interface with `@HttpExchange` methods. Boot 4 registers the proxy with
`@ImportHttpServices`, so do not create a manual factory for every client.

## Define and register the interface

```java
@HttpExchange("/orders")
public interface OrderApiClient {
    @GetExchange("/{id}")
    OrderDto get(@PathVariable UUID id);

    @PostExchange
    OrderDto create(@RequestBody CreateOrderRequest request);
}

@SpringBootApplication
@ImportHttpServices(group = "orders", basePackages = "com.example.client.orders")
public class Application { }
```

Inject `OrderApiClient` like any other bean. The interface needs no `@Component` and no
implementation class.

## Configure groups and transport

```yaml
spring:
  http:
    clients:
      connect-timeout: 2s
      read-timeout: 5s
    serviceclient:
      orders:
        base-url: https://orders.internal.example.com
        read-timeout: 10s
```

`spring.http.clients.*` contains global transport defaults. `spring.http.serviceclient.<group>.*`
contains per-group settings, and the group name must match `@ImportHttpServices`.

The default client type is blocking `RestClient`. Select
`HttpServiceGroup.ClientType.WEB_CLIENT` for interfaces returning `Mono` or `Flux`, and include
the WebClient starter. Group configurers apply to a group, not one interface.

Manual `HttpServiceProxyFactory` wiring remains valid for one-off clients, but it is the Boot 3
pattern and should not be the default in Boot 4.

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

The repository fixture targets Boot 4.1.0 with Java 17/21 and checks
the imperative catalog adapter against a local HTTP server; not every declarative group-registration option. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas

- Agent creates a `HttpServiceProxyFactory` bean for every client - use `@ImportHttpServices` and groups.
- Agent adds `@Component` or an implementation to the interface - generated proxies need neither.
- Agent uses `spring.http.client.*` - global defaults are under `spring.http.clients.*`; group settings use `serviceclient`.
- Agent hard-codes a host in `@HttpExchange` and sets a group base URL - keep the host in configuration.
- Agent returns `Mono` or `Flux` with the default client - select `WEB_CLIENT` explicitly.
- Agent assumes a group configurer customizes one interface - configure the group or separate the group.
