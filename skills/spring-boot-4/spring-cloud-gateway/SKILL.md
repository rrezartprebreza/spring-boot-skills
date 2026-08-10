---
name: spring-cloud-gateway
description: >
  Use when building or changing Spring Cloud Gateway on Spring Boot 4. Covers release-train
  compatibility, routes, authentication, header hygiene, rate limits, timeouts, and resilience.
---

# Spring Cloud Gateway

Keep the gateway an edge adapter. Do not move domain workflows into filters.

## Compatibility first

- Confirm that a Spring Cloud release train explicitly supports the exact Boot 4 line.
- Import the Spring Cloud BOM and omit individual Spring Cloud dependency versions.
- Do not force a Boot 3 release train onto Boot 4 to resolve dependency conflicts.
- Choose reactive or MVC gateway deliberately and use its matching starter and test support.

## Route rules

- Use stable route IDs and explicit predicates.
- Strip client-provided forwarding, identity, and internal headers.
- Recreate correlation headers only after removing untrusted values.
- Keep path/host rewriting visible and covered by tests.
- Set global connect and response timeouts with narrow route overrides.

## Security and resilience

- Authenticate at the edge and authorize again in downstream services.
- Relay tokens only to services with the intended audience.
- Rate-limit by authenticated identity or trusted API key.
- Retry only proven idempotent operations and only before response commitment.
- Use circuit breakers and bounded fallbacks; expose sustained upstream failure.

## Testing and operations

- Test predicates, filters, headers, status mapping, CORS, timeouts, and body size limits.
- Use a controlled upstream server for integration tests.
- Record route ID, outcome, latency, rate-limit decisions, and upstream failures with bounded tags.

## Examples

- See `examples/good-routes.yml` and `examples/bad-routes.yml`.

## Gotchas

- Agent selects a Spring Cloud version without checking Boot 4 support - use the compatibility matrix.
- Agent trusts a public identity header - derive identity after authentication.
- Agent retries POST requests automatically - retry only proven idempotent operations.
- Agent puts business orchestration in gateway filters - keep domain logic downstream.
- Agent omits response timeouts - stalled upstreams can exhaust resources.
