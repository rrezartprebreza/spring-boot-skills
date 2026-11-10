---
name: scheduled-tasks
description: >
  Use when creating or reviewing @Scheduled jobs, cron/time-zone configuration, overlapping runs or multi-instance scheduling. Not for Spring Batch chunk/restart mechanics or arbitrary async dispatch.
---

# Scheduled Tasks

## Spring Boot 4 baseline

Use Framework 7 scheduling APIs and the project's Boot BOM; the template uses Java 17.
For reactive scheduled methods, create deferred repeatable work rather than a precomputed
result. Virtual-thread scheduling requires Java 21 and explicit Boot configuration.

## Trigger contract

Establish schedule, time zone, maximum run duration, missed-run behavior and replica count
before choosing an implementation. Enable scheduling once. Spring cron has six fields,
including seconds. Specify a zone rather than relying on a deployment host default, and test
daylight-saving transitions when the business contract uses local time.

Fixed delay measures from completion; fixed rate follows a start-time cadence. Neither is a
cluster-wide lock. Adding @Async detaches completion from the scheduled invocation and can
allow overlapping work. Multiple scheduled declarations or bean instances can also duplicate
execution. Do not change an existing cadence just to hide a slow job.

[StaleOrderSchedule](templates/StaleOrderSchedule.java) is a thin, disabled-by-default cron
adapter. Supply a Clock and a SweepAction bean; enable @EnableScheduling in configuration.
[Good configuration](examples/good-schedule.yml) requires an explicit cron to enable it;
[the bad example](examples/bad-overlap.java) starts untracked duplicate work.

## Replicas and durable work

Every application instance can register the trigger. Decide whether all replicas may work,
one leader triggers, or workers claim disjoint persisted items. Reuse existing coordination
(for example a clustered scheduler or a tested lease library); do not invent a JVM boolean
lock or call Redis SETNX without ownership, expiry and stale-owner handling.

A lease can expire while the original worker is still running. Use idempotent effects and,
where stale owners can corrupt state, fencing checked by the protected resource. A lock alone
does not provide exactly-once effects. Persist progress and retry intent for mandatory work;
Spring's in-process schedule does not replay missed fires after downtime.

Keep the scheduling adapter small. Let a separate service claim a bounded batch and enforce
tenant boundaries and transaction rules. Avoid one transaction over an unbounded scan or a
remote call. Handle uncertain external outcomes with provider idempotency or reconciliation.
The template's SweepAction is an application integration point, not an implemented lock or queue.

## Operations and verification

Set runtime and I/O limits. Observe last successful completion, duration, failures and backlog
age using bounded metric labels. Logging an exception is not a retry or an alerting policy.
Use the project's scheduler shutdown policy and deployment grace period; interruption does
not automatically roll back remote effects. Virtual threads still need downstream limits;
fixed-delay tasks on SimpleAsyncTaskScheduler share its scheduler thread.

Test cron parsing and disabled configuration without waiting for wall-clock runs. Inject a
Clock into time-dependent work. Add integration tests for competing replicas, lease expiry,
partial failure and restart when using coordination. The included fixture checks the adapter
and trigger registration only; it does not implement distributed scheduling.

## Gotchas

- Agent uses a five-field Unix cron - include Spring's seconds field and verify next fire times.
- Agent assumes @Scheduled runs once across replicas - define explicit coordination or safe duplicate work.
- Agent adds @Async to fix a slow timer - bound and test overlapping execution instead.
- Agent relies on a lease for exactly-once effects - account for expiry, stale owners and duplicates.
- Agent silently enables a destructive maintenance job - require explicit configuration and authorization.
- Agent tests timing with long sleeps - use a fixed Clock and direct trigger calculations.

## Official sources

- [Boot 4 scheduling](https://docs.spring.io/spring-boot/4.1/reference/features/task-execution-and-scheduling.html)
- [Framework task scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Scheduled annotation and disabled cron](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
