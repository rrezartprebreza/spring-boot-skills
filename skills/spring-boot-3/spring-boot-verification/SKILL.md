---
name: spring-boot-verification
description: >
  Use when assessing a Spring Boot project's readiness before a pull request or release, or when asked to run its build, static analysis, tests and security checks. Not a deployment or permission to repair unrelated code.
---

# Spring Boot 3 Verification

Produce an evidence-backed readiness report, not a blanket "production-ready" verdict.
Inspect the repository's build files, wrappers, CI and contribution instructions first.

## Select the actual checks

- Establish the effective Boot 3 version, JDK used by Maven/Gradle, toolchain and target
  release. A JDK installed elsewhere does not fix the JVM running the build.
- Preserve the build tool, profiles, module boundaries and existing quality thresholds.
  Check wrapper provenance before execution; build scripts and plugins execute code.
  Derive mandatory gates from repository/CI policy; disclose ambiguity instead of inventing gates.
- List the checks and their external requirements before running. Use disposable dependencies.
  A verification request does not authorize production access, credential uploads or deployment.
- Use the project's configured wrapper and CI commands. For Maven, `./mvnw verify`
  runs the lifecycle through integration-test verification **only if** Failsafe is bound.
  Do not stop at `integration-test`: teardown and failure verification occur later.
- For Gradle, inspect configured tasks and their dependencies. `./gradlew check` does not
  guarantee a separately registered `integrationTest` task runs; invoke it only if it exists
  and is not already connected. Preserve Kotlin/Groovy DSL and version catalogs.
- Run configured lint/static analysis and coverage gates, not arbitrary plugin goals.
  A JaCoCo report is not an enforced threshold; confirm a check task/goal is wired.
- Scope vulnerability/secret scanning to the request and approved tools. State advisory
  freshness, scan failures and exclusions. Missing scanners are NOT a clean result.

## Version-sensitive checks

Use Boot 3 dependencies and the project's test conventions. Do not introduce Framework 7
APIs or Boot 4 test starters to make tests pass. Distinguish older Boot 3 minors from 3.5.

Compare test discovery and counts with the expected suites. Zero executed tests, disabled
tests, skipped containers and stale reports must be visible, even when the build exits zero.
Capture exit status without masking it with a pipe. Do not delete useful prior evidence;
identify reports belonging to this invocation by run location/time and source revision.

## Report and stopping conditions

Record revision plus dirty state, Boot/JDK/build-tool versions, exact commands, exit status,
executed test counts, report locations and remaining risks. Mark each gate PASS, FAIL,
BLOCKED or NOT RUN. A blocked mandatory check means readiness is not established.
After a deterministic failure, stop dependent checks and explain the cause. Retry a proven
transient infrastructure failure at most once; retain both outcomes. Do not weaken gates,
disable security, raise coverage exclusions or commit/push/merge to obtain green checks.
Fix only within separately authorized implementation scope.

Use [the report example](examples/good-report.md) for evidence shape and
[the bad report](examples/bad-report.md) for common false conclusions.

## Official sources

- [Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
- [Failsafe lifecycle and verification](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html)
- [Gradle Java plugin tasks](https://docs.gradle.org/current/userguide/java_plugin.html)
- [JaCoCo check](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html)
- [Boot 3 testing](https://docs.spring.io/spring-boot/3.5/reference/testing/index.html)

## Gotchas

- Agent reports success from exit code zero with zero tests - verify discovery and intended suites.
- Agent runs Maven test only - include configured integration tests and verification gates.
- Agent treats generated coverage HTML as a gate - inspect threshold enforcement.
- Agent installs new scanners or bypasses failed checks - report prerequisites and preserve policy.
