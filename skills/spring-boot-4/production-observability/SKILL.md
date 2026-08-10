---
name: production-observability
description: >
  Use when adding Spring Boot 4 Actuator, Micrometer Observation and Tracing, OpenTelemetry or
  OTLP export, structured logging, health probes, dashboards, or production alerting.
---

# Production Observability

Instrument user-visible operations and operational boundaries, not every method.

## Baseline

- Add the Boot 4 Actuator starter and one metrics registry selected by the platform.
- Use Micrometer Observation for application metrics and traces.
- Prefer Micrometer APIs over direct OpenTelemetry APIs in application code.
- Export metrics and traces through OTLP when an OpenTelemetry collector is the platform boundary.
- Expose only required actuator endpoints and secure non-public endpoints.

## Observation conventions

- Name observations by stable operation, such as `orders.create`.
- Use low-cardinality key values for metrics and high-cardinality values only for traces.
- Never tag metrics with user IDs, entity IDs, URLs containing IDs, or exception messages.
- Register a `ContextPropagatingTaskDecorator` for async executor boundaries.
- Enable Reactor automatic context propagation only deliberately and test it.
- Avoid annotating already instrumented MVC controllers or repositories with `@Observed`.

## Health and readiness

- Keep liveness independent from remote dependencies.
- Put traffic-critical dependencies in readiness groups.
- Give custom health checks strict timeouts and stable detail keys.
- Keep sensitive health details hidden from unauthenticated callers.

## Logging and alerts

- Emit structured logs correlated with trace and span IDs.
- Redact credentials, tokens, personal data, prompts, and request bodies by default.
- Alert on service-objective symptoms: error ratio, latency, saturation, or queue lag.
- Include a runbook and service/dependency identity in every actionable alert.

## Examples

- See `examples/good-observation.java` and `examples/bad-observation.java`.

## Gotchas

- Agent uses high-cardinality IDs as metric tags - reserve them for traces or logs.
- Agent directly configures an OpenTelemetry SDK and disables Boot integration accidentally - prefer Boot and Micrometer support.
- Agent exposes all actuator endpoints - expose the minimum and secure them.
- Agent ties liveness to a database or broker - dependency outages can trigger restart loops.
- Agent loses trace context in `@Async` work - register context propagation explicitly.
