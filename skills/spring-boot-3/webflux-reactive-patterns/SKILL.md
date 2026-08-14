---
name: webflux-reactive-patterns
description: >
  Use when the complete Spring Boot 3 request path is reactive: WebFlux controllers, Reactor
  pipelines, R2DBC persistence, streaming, backpressure, or cancellation. For defining and
  registering declarative outbound API interfaces, use http-interface-clients.
---

# WebFlux Reactive Patterns

Use WebFlux when the complete request path can benefit from non-blocking I/O.

## Pipeline rules

- Return `Mono` or `Flux`; do not call `subscribe()` in request-handling code.
- Compose work with `map`, `flatMap`, `concatMap`, and `zip` according to ordering requirements.
- Keep blocking JDBC, filesystem, and legacy SDK calls out of event-loop threads.
- Isolate unavoidable blocking calls on `boundedElastic` at the narrow adapter boundary.
- Apply timeouts at remote-call boundaries and preserve cancellation.

## Context and errors

- Put request-scoped metadata in Reactor `Context`, not `ThreadLocal`.
- Translate domain failures centrally without swallowing cancellation or infrastructure errors.
- Use `onErrorResume` only when a defined fallback exists.
- Avoid `onErrorContinue`; it makes partial processing hard to reason about.

## Persistence and streaming

- Use R2DBC for reactive database access; JDBC makes the pipeline blocking.
- Bound concurrency for fan-out operations.
- Define backpressure and buffering limits for streaming endpoints.
- Avoid collecting an unbounded `Flux` into memory.

## Testing

- Use `StepVerifier` for publisher behavior and `WebTestClient` for HTTP contracts.
- Test cancellation, timeout, empty results, errors, ordering, and backpressure.
- Enable blocking-call detection in tests when the project supports it.

## Examples

- See `examples/good-reactive-service.java` and `examples/bad-reactive-service.java`.

## Gotchas

- Agent calls `block()` in a controller or service - keep the full request path non-blocking.
- Agent calls `subscribe()` manually - the web runtime owns subscription.
- Agent stores tenant or security data in `ThreadLocal` - use Reactor `Context`.
- Agent uses unbounded `flatMap` - set concurrency according to downstream capacity.
- Agent wraps JDBC in `Mono.just` - that still blocks the caller thread.
