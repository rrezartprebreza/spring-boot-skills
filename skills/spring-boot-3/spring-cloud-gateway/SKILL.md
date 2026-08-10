---
name: spring-cloud-gateway
description: >
  Use when building or changing a Spring Cloud Gateway on Spring Boot 3. Covers route design,
  authentication, header hygiene, rate limiting, retries, timeouts, observability, and testing.
---

# Spring Cloud Gateway

Keep the gateway an edge adapter. Do not move domain workflows into filters.

## Compatibility first

- Select the Spring Cloud release train compatible with the exact Boot 3 minor.
- Import the Spring Cloud BOM and omit individual Spring Cloud dependency versions.
- Prefer the reactive gateway unless the project deliberately uses the MVC variant.

## Route rules

- Use stable route IDs and explicit predicates.
- Strip untrusted forwarding, identity, and internal headers at the edge.
- Add correlation headers only after removing client-supplied values.
- Keep path rewriting and host changes visible in route configuration.
- Set connect and response timeouts globally, with narrow route overrides.

## Security and resilience

- Authenticate at the edge and authorize again in downstream services.
- Relay tokens only to intended audiences.
- Rate-limit by trusted identity or API key, not an unverified header.
- Retry only idempotent operations and only before response commitment.
- Use circuit breakers for failing dependencies; never hide sustained failure with unlimited retries.

## Testing and operations

- Test predicates, filters, header removal, status mapping, and timeouts.
- Use WireMock or a containerized upstream for integration tests.
- Emit route ID, outcome, latency, and upstream metrics with bounded tags.

## Examples

- See `examples/good-routes.yml` and `examples/bad-routes.yml`.

## Gotchas

- Agent trusts `X-User-Id` from the public request - derive identity after authentication.
- Agent retries POST requests by default - retry only operations proven idempotent.
- Agent adds business orchestration to a gateway filter - keep domain logic downstream.
- Agent omits response timeouts - stalled upstreams can exhaust gateway resources.
- Agent mixes incompatible Spring Cloud and Boot versions - use the official compatibility matrix.
