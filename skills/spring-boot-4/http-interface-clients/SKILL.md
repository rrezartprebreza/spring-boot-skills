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

## Gotchas

- Agent creates a `HttpServiceProxyFactory` bean for every client - use `@ImportHttpServices` and groups.
- Agent adds `@Component` or an implementation to the interface - generated proxies need neither.
- Agent uses `spring.http.client.*` - global defaults are under `spring.http.clients.*`; group settings use `serviceclient`.
- Agent hard-codes a host in `@HttpExchange` and sets a group base URL - keep the host in configuration.
- Agent returns `Mono` or `Flux` with the default client - select `WEB_CLIENT` explicitly.
- Agent assumes a group configurer customizes one interface - configure the group or separate the group.
