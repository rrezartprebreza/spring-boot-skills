---
name: spring-cloud-gateway
description: >
  Use when building Spring Cloud Gateway 5 on Spring Boot 4, including WebFlux or Web MVC routes,
  edge authentication, trusted headers, rate limits, timeouts, and resilience. Do not use for
  ordinary outbound HTTP clients or domain orchestration.
---

# Spring Cloud Gateway 5

Keep the gateway an edge adapter. Do not move domain workflows into filters.

## Compatibility and dependencies

- Use the latest Spring Cloud `2025.1.x` release train for Spring Boot 4.
- Boot 4.1 support starts with Spring Cloud `2025.1.2`.
- Import `spring-cloud-dependencies` as a BOM and omit versions from individual Cloud dependencies.
- Choose exactly one gateway runtime.

```xml
<!-- Reactive Netty gateway -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>

<!-- OR servlet gateway: spring-cloud-starter-gateway-server-webmvc -->
```

Do not use the old `spring-cloud-starter-gateway` coordinate or force a Boot 3 release train onto
Boot 4. WebFlux requires Reactor Netty and cannot run as a traditional servlet WAR; Web MVC uses a
servlet runtime and WebMvc.fn.

## Gateway 5 configuration namespaces

Gateway 5 separates server implementations in configuration:

- WebFlux: `spring.cloud.gateway.server.webflux.*`
- Web MVC: `spring.cloud.gateway.server.webmvc.*`

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          httpclient:
            connect-timeout: 2000
            response-timeout: 5s
          routes:
            - id: orders-api
              uri: http://orders.internal
              predicates:
                - Path=/api/orders/**
              filters:
                - RemoveRequestHeader=X-User-Id
```

The Boot 3-era `spring.cloud.gateway.routes` and `spring.cloud.gateway.httpclient` paths do not bind
to Gateway 5's WebFlux server properties.

## Route and security rules

- Use stable route IDs and explicit predicates.
- Remove client-provided forwarding, identity, and internal headers before creating trusted values.
- Keep path and host rewriting visible and covered by tests.
- Authenticate at the edge and authorize again in downstream services.
- Relay tokens only to services with the intended audience.
- Rate-limit by authenticated identity or a verified API key, not an untrusted address header.
- Set global connection and response timeouts with narrow route overrides.
- Retry only proven idempotent operations and only before response commitment.
- Keep fallbacks bounded and expose sustained upstream failure.

## Testing and operations

- Test predicates, filters, trusted headers, CORS, timeouts, body limits, and status mapping.
- Use a controlled upstream server rather than mocking gateway internals.
- Record route ID, outcome, latency, and upstream failure with bounded metric tags.
- Test each selected runtime with its matching Spring Cloud Gateway test support.

## Examples

- `examples/good-routes.yml` shows Gateway 5 WebFlux configuration.
- `examples/good-webmvc-routes.yml` shows Gateway 5 Web MVC configuration.
- `examples/bad-routes.yml` preserves the stale configuration as a warning.

## Official sources

- Spring Cloud compatibility: https://spring.io/projects/spring-cloud/
- Gateway WebFlux starter: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/starter.html
- Gateway Web MVC starter: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/starter.html
- Gateway configuration properties: https://docs.spring.io/spring-cloud-gateway/reference/configprops.html

## Gotchas

- Agent uses `spring.cloud.gateway.routes` on Gateway 5 - use the server-specific namespace.
- Agent uses `spring-cloud-starter-gateway` on Boot 4 - select the WebFlux or Web MVC server starter.
- Agent selects a Cloud train without checking the exact Boot line - use the compatibility matrix.
- Agent trusts a public identity header - derive identity after authentication.
- Agent retries POST requests automatically - retry only proven idempotent operations.
- Agent puts business orchestration in filters - keep domain logic downstream.
- Agent omits response timeouts - stalled upstreams can exhaust resources.
