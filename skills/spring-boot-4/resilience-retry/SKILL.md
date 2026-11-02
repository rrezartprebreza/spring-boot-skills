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

See the [official Framework resilience reference](https://docs.spring.io/spring-framework/reference/core/resilience.html).

## Enable resilient methods

```java
@Configuration
@EnableResilientMethods
class ResilienceConfig { }
```

Use `@Retryable` for transient failures and `@ConcurrencyLimit` as a lightweight bulkhead:

```java
@Service
class CatalogClient {
    @Retryable(
        includes = ConnectException.class,
        maxRetries = 4,
        delay = 200,
        multiplier = 2.0,
        maxDelay = 2000,
        jitter = 50)
    CatalogItem find(UUID id) { ... } // idempotent read
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

## Failure and deadline policy

- Classify exceptions at the transport boundary; HTTP clients may wrap connect/timeout errors.
  Retry only failures approved for that operation, not arbitrary `Exception`.
- A write timeout means the outcome may be unknown. Retry payment/order commands only with
  provider-supported idempotency and the same key and payload on every attempt. Reconcile
  uncertain outcomes; never turn exhaustion into a fabricated successful payment.
- Budget all attempts, backoff, connection-pool acquisition and downstream work within the
  caller's deadline. Per-attempt connect/read timeouts are not a total operation deadline.
  A retry timeout is not a mechanism for killing an already-running blocking call.
- Preserve interruption/cancellation and stop scheduling attempts when the budget is exhausted.
  Do not layer independent retries at gateway, HTTP client and service without a shared budget.
- Treat authentication/authorization errors and validation errors as permanent. Handle 409
  according to the API contract. Honor bounded `Retry-After` for 429/503 only when policy permits;
  do not keep a request thread asleep beyond the caller's deadline.

The [CatalogRetryService](templates/CatalogRetryService.java) is an executable, three-attempt
read example, used with the companion HTTP-client adapter. It deliberately does not implement
429 scheduling or a total deadline. Tests cover transient recovery, exhaustion, permanent
errors and interruption through the actual Spring proxy. Its delays are illustrative; choose
production values from the downstream latency budget.

## Verification scope

The repository fixture targets Boot 4.1.0 with Java 17/21 and checks
bounded attempts through real Spring proxies; not a complete deadline-aware payment workflow. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas

- Agent adds `spring-retry` for basic core retry - use `@EnableResilientMethods` with Framework 7.
- Agent imports `org.springframework.retry.annotation` - core annotations use `org.springframework.resilience.annotation`.
- Agent writes `maxAttempts` - core uses `maxRetries`, which counts retries rather than total calls.
- Agent adds `@Recover` - core resilience has no recovery callback; handle the final exception at the caller.
- Agent nests `@Backoff` - use flat `delay`, `multiplier`, `maxDelay`, and `jitter` attributes.
- Agent forgets `@EnableResilientMethods` - annotations are ignored without enablement.
- Agent retries a self-invoked method - call through a Spring proxy.
- Agent retries inside a rollback-only transaction - move retry outside the transactional boundary.
