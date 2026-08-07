---
name: resilience-retry
description: >
  Use when adding retries, backoff, or concurrency limits to Spring Boot 4 / Spring Framework 7
  code with core Retryable and ConcurrencyLimit support. Covers enablement, backoff, proxy limits,
  and behavior after retries are exhausted.
---

# Core Resilience (Boot 4 / Framework 7)

Spring Framework 7 provides core declarative retry and concurrency limiting. This is not a
repackaging of the separate `spring-retry` project; attribute names and recovery behavior differ.

## Enable resilient methods

```java
@Configuration
@EnableResilientMethods
class ResilienceConfig { }
```

Use `@Retryable` for transient failures and `@ConcurrencyLimit` as a lightweight bulkhead:

```java
@Service
class PaymentClient {
    @Retryable(
        includes = ConnectException.class,
        maxRetries = 4,
        delay = 200,
        multiplier = 2.0,
        maxDelay = 2000,
        jitter = 50)
    PaymentResult charge(ChargeRequest request) { ... }
}

@ConcurrencyLimit(4)
Report generateReport(UUID id) { ... }
```

`maxRetries` counts retries after the first call, so four retries means five total attempts.
Backoff attributes are flat on `@Retryable`; `multiplier = 1.0` means fixed delay.

Core resilience has no `@Recover`. When attempts are exhausted, the last exception reaches the
caller. Catch it at the orchestration boundary or use a listener. Because the implementation is
proxy-based, self-invocation bypasses advice. Put retry outside a transactional bean when each
attempt needs a fresh transaction.

## Gotchas

- Agent adds `spring-retry` for basic core retry - use `@EnableResilientMethods` with Framework 7.
- Agent imports `org.springframework.retry.annotation` - core annotations use `org.springframework.resilience.annotation`.
- Agent writes `maxAttempts` - core uses `maxRetries`, which counts retries rather than total calls.
- Agent adds `@Recover` - core resilience has no recovery callback; handle the final exception at the caller.
- Agent nests `@Backoff` - use flat `delay`, `multiplier`, `maxDelay`, and `jitter` attributes.
- Agent forgets `@EnableResilientMethods` - annotations are ignored without enablement.
- Agent retries a self-invoked method - call through a Spring proxy.
- Agent retries inside a rollback-only transaction - move retry outside the transactional boundary.
