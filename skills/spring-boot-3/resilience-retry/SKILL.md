---
name: resilience-retry
description: >
  Use when adding retries, backoff, or concurrency protection to Spring Boot 3 / Spring Framework 6
  services. Covers Spring Retry or Resilience4j selection, proxy behavior, and transaction limits.
---

# Resilience and Retry (Boot 3)

Boot 3 does not have Spring Framework 7 core resilience annotations. Preserve an existing
Spring Retry or Resilience4j integration when fixing behavior; do not silently replace it.
[Spring Retry is archived](https://github.com/spring-attic/spring-retry), so the example below
is for compatibility with existing Boot 3 applications, not a new-project default.
For new retry infrastructure, evaluate a maintained Boot-3-compatible library such as
Resilience4j and its exact Java/framework requirements. Keep dependencies explicit.

```java
@Configuration
@EnableRetry
class RetryConfig { }

@Service
class CatalogClient {
    @Retryable(
        retryFor = ConnectException.class,
        maxAttempts = 4,
        backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000))
    CatalogItem find(UUID id) { ... } // idempotent read

    @Recover
    CatalogItem recover(ConnectException error, UUID id) { throw new CatalogUnavailableException(error); }
}
```

Spring Retry's `maxAttempts` includes the initial call. Resilience4j is preferable when retry
must be composed with a circuit breaker or bulkhead. Both approaches are proxy-based: self
invocation bypasses advice, and retrying a method inside a rollback-only transaction does not
create a fresh transaction for each attempt.

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

The [CatalogRetryService](templates/CatalogRetryService.java) preserves Spring Retry for an
existing application and requires `spring-retry` plus Spring AOP/AspectJ support.
It is an executable, three-attempt
read example, used with the companion HTTP-client adapter. It deliberately does not implement
429 scheduling or a total deadline. Tests cover transient recovery, exhaustion, permanent
errors and interruption through the actual Spring proxy. Its delays are illustrative; choose
production values from the downstream latency budget.

## Verification scope

The repository fixture targets Boot 3.5.16 with Java 17/21 and checks
bounded attempts through real Spring proxies; not a complete deadline-aware payment workflow. Check the
[coverage matrix](https://github.com/rrezartprebreza/spring-boot-skills/blob/main/verification/MATRIX.md)
before treating another minor version or an illustrative snippet as verified.

## Gotchas

- Agent uses Framework 7 `@EnableResilientMethods` - Boot 3 needs Spring Retry or Resilience4j.
- Agent confuses `maxAttempts` with retry count - Spring Retry includes the initial call.
- Agent adds `@Recover` to Resilience4j - recovery must be modeled with a fallback or caller handling.
- Agent retries a self-invoked method - call through a Spring proxy.
- Agent retries inside a transaction expecting a fresh transaction - move retry outside the transactional bean.
- Agent retries non-idempotent writes without an idempotency key - protect payment and command operations.
