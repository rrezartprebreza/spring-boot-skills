---
name: production-observability
description: >
  Use when adding service-wide Spring Boot 3 Actuator, Micrometer metrics, distributed tracing,
  OpenTelemetry export, structured logging, health probes, dashboards, or alerts. Combine with
  ai-observability only for Spring AI-specific token, prompt, and model telemetry.
---

# Production Observability

Instrument user-visible operations and operational boundaries, not every method.

## Baseline

- Add Actuator and one metrics registry selected by the deployment platform.
- Use Micrometer Observation for application metrics and traces.
- Use Micrometer Tracing with the chosen bridge; do not mix tracing APIs throughout business code.
- Export through OTLP when the platform standardizes on OpenTelemetry collectors.
- Expose only required actuator endpoints and secure every non-public endpoint.

## Observation conventions

- Name observations by stable operation, such as `orders.create`.
- Keep metric tags low-cardinality: method, outcome, region, or bounded status.
- Put request IDs, user IDs, order IDs, and exception messages only in traces or logs.
- Propagate context across `@Async`, executor, and Reactor boundaries.
- Record latency, throughput, failures, and saturation for every external dependency.

## Health and readiness

- Keep liveness independent from remote systems so a dependency outage does not restart every pod.
- Put required dependencies in readiness groups.
- Write custom health indicators only for dependencies that affect traffic acceptance.
- Set explicit timeouts on health checks.

## Logging and alerts

- Emit structured logs with trace and span correlation.
- Redact credentials, tokens, personal data, prompts, and payloads by default.
- Alert on symptoms tied to service objectives, not raw metric noise.
- Include runbook links and enough dimensions to identify the affected service and dependency.

## Examples

- See `examples/good-observation.java` and `examples/bad-observation.java`.

## Gotchas

- Agent tags metrics with user or entity IDs - this creates unbounded cardinality.
- Agent exposes every actuator endpoint publicly - expose the minimum and secure it.
- Agent makes liveness depend on the database - dependency outages then cause restart loops.
- Agent logs request bodies and tokens for debugging - redact sensitive data before emission.
- Agent creates spans but loses context in async work - configure context propagation explicitly.
