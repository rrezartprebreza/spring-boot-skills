---
name: async-execution
description: >
  Use when adding or debugging Spring @Async work, executor saturation, asynchronous failures or lost thread context. Not a substitute for durable jobs or reactive pipelines.
---

# Async Execution

## Spring Boot 4 baseline

Use Framework 7 APIs and the project's Boot BOM. The shipped examples compile on Java 17.
Virtual threads require Java 21 and explicit Boot configuration. They are not enabled merely
by upgrading to Boot 4. Preserve the project's executor choices and check the exact minor.

## Choose the execution boundary

First decide whether losing work on process exit is acceptable. In-memory async work is not
a durable queue. Required notifications, payments and accepted background jobs need persisted
intent plus recovery, not just an AFTER_COMMIT listener or an HTTP 202 response.
Keep an existing broker or job framework when it already owns this contract.

Enable async interception once and call a separate Spring bean through its proxy. A same-class
call does not become asynchronous. Use a named executor and return CompletableFuture when the
caller must observe failure. A void method needs an AsyncUncaughtExceptionHandler; the caller
cannot catch its later failure. Rejection can happen synchronously during submission.

See [the bounded executor](templates/ReportExecutorConfiguration.java) and
[the worker](templates/ReportTasks.java). Sizes are example budgets, not universal defaults.
[Good usage](examples/good-report-call.java) separates rejected submissions from failed work;
[bad usage](examples/bad-self-invocation.java) hides both the execution and capacity problems.

## Capacity and lifecycle

Bound threads and queued work against downstream capacity and latency targets. A large queue
can hide overload and delay growth beyond the core pool. Define a visible rejection outcome;
do not silently discard required work. CallerRunsPolicy can move blocking work onto an HTTP
or event-loop thread, so it is not a universal backpressure fix.

A custom Executor can change Boot auto-configuration. Preserve the application's existing
applicationTaskExecutor and MVC/WebFlux integration; do not paste the sample configuration
over it. The sample explicitly selects reportExecutor and only covers that worker.
Virtual threads do not increase database connections or remote quotas: bound outstanding work
even without a platform-thread pool. Configure I/O timeouts and bounded shutdown separately.
Future cancellation/timeout is not proof that an external operation stopped.

## State and transactions

Pass immutable IDs or snapshots, not request objects, managed entities or lazy collections.
The caller's thread-bound transaction does not move with the task. Put any worker transaction
on a separate proxied service and load its state there. Starting async work before commit can
observe missing data; after-commit dispatch still has a crash-loss window.

Tenant, MDC and security state need deliberate propagation and cleanup. Capture only required,
trusted context; restore prior worker state in finally. Use Spring Security's delegating
executor support when the operation must run with the submitting security context. Do not
use inheritable thread locals as a pool-wide tenant or authentication strategy.

## Verification

Test via the actual proxy: worker thread selection, failed futures, saturation rejection and
shutdown. Use latches with deadlines, not sleep-based assumptions. Test context cleanup with
successive users on the same worker and database visibility at the commit boundary. Report
which tests were actually run; the provided fixture does not certify durable delivery.

## Gotchas

- Agent calls its own @Async method - invoke a separate managed bean through the proxy.
- Agent calls supplyAsync without an executor inside the worker - avoid a second common-pool hop.
- Agent treats an unbounded queue as overload protection - set and test an explicit capacity budget.
- Agent catches only future failures - handle synchronous task rejection too.
- Agent assumes AFTER_COMMIT plus async guarantees delivery - persist required work for recovery.
- Agent copies tenant or security state without cleanup - prevent leakage between pooled tasks.

## Official sources

- [Boot 4 task execution](https://docs.spring.io/spring-boot/4.1/reference/features/task-execution-and-scheduling.html)
- [Framework scheduling and async interception](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Async return types and executor selection](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [ThreadPoolTaskExecutor capacity](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Security concurrency support](https://docs.spring.io/spring-security/reference/features/integrations/concurrency.html)
- [Transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
