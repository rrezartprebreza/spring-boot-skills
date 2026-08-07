---
name: resilience-retry
description: >
  Use when adding retries, backoff, or concurrency protection to Spring Boot 3 / Spring Framework 6
  services. Covers Spring Retry or Resilience4j selection, proxy behavior, and transaction limits.
---

# Resilience and Retry (Boot 3)

Boot 3 does not have Spring Framework 7 core resilience annotations. Choose Spring Retry for
simple imperative retry or Resilience4j when you also need circuit breakers, rate limiting, or
bulkheads. Keep the dependency and configuration explicit.

```java
@Configuration
@EnableRetry
class RetryConfig { }

@Service
class PaymentClient {
    @Retryable(
        retryFor = ConnectException.class,
        maxAttempts = 4,
        backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000))
    PaymentResult charge(ChargeRequest request) { ... }

    @Recover
    PaymentResult recover(ConnectException error, ChargeRequest request) { ... }
}
```

Spring Retry's `maxAttempts` includes the initial call. Resilience4j is preferable when retry
must be composed with a circuit breaker or bulkhead. Both approaches are proxy-based: self
invocation bypasses advice, and retrying a method inside a rollback-only transaction does not
create a fresh transaction for each attempt.

## Gotchas

- Agent uses Framework 7 `@EnableResilientMethods` - Boot 3 needs Spring Retry or Resilience4j.
- Agent confuses `maxAttempts` with retry count - Spring Retry includes the initial call.
- Agent adds `@Recover` to Resilience4j - recovery must be modeled with a fallback or caller handling.
- Agent retries a self-invoked method - call through a Spring proxy.
- Agent retries inside a transaction expecting a fresh transaction - move retry outside the transactional bean.
- Agent retries non-idempotent writes without an idempotency key - protect payment and command operations.
