---
name: webflux-reactive-patterns
description: >
  Use when the complete Spring Boot 4 request path is reactive: WebFlux controllers, Reactor
  pipelines, R2DBC persistence, streaming, backpressure, or cancellation. For defining and
  registering declarative outbound API interfaces, use http-interface-clients.
---

# WebFlux Reactive Patterns

Use WebFlux when the complete request path benefits from non-blocking I/O.

## Dependencies and pipeline rules

- Use the dedicated Boot 4 WebFlux and WebFlux test starters.
- Return `Mono` or `Flux`; never call `subscribe()` in request-handling code.
- Choose `flatMap`, `concatMap`, or sequential composition according to ordering needs.
- Keep JDBC, filesystem, and blocking SDK calls off Reactor event loops.
- Isolate unavoidable blocking work on `boundedElastic` at the adapter boundary.
- Apply timeouts to external calls and preserve cancellation.

## Context and errors

- Put request-scoped metadata in Reactor `Context`, not `ThreadLocal`.
- Configure observability context propagation deliberately.
- Translate domain failures centrally without swallowing cancellation.
- Use `onErrorResume` only for a defined fallback and avoid `onErrorContinue`.

## Persistence and streaming

- Use R2DBC for reactive database access.
- Bound fan-out concurrency according to downstream capacity.
- Define backpressure, buffering, and maximum in-memory sizes.
- Never collect an unbounded stream solely to simplify downstream code.

## Testing

- Use `StepVerifier` for publisher behavior and `WebTestClient` for contracts.
- Test cancellation, timeout, empty results, errors, ordering, and backpressure.
- Detect blocking calls in tests when practical.

## Examples

- See `examples/good-reactive-service.java` and `examples/bad-reactive-service.java`.

## Official sources

- Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Reactor reference: https://projectreactor.io/docs/core/release/reference/
- Spring Data R2DBC: https://docs.spring.io/spring-data/relational/reference/r2dbc.html

## Gotchas

- Agent adds MVC and WebFlux starters accidentally - choose the intended web stack.
- Agent calls `block()` or `subscribe()` in application flow - keep subscription with the runtime.
- Agent uses `ThreadLocal` for tenant or trace data - use Reactor `Context`.
- Agent uses unbounded `flatMap` - set concurrency from downstream limits.
- Agent wraps JDBC with `Mono.just` - this still blocks the event loop.
