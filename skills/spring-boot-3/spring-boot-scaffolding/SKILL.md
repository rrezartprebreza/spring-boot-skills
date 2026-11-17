---
name: spring-boot-scaffolding
description: >
  Use when creating a new Spring Boot application with a verified build, tests and minimal CI. Select compatible Spring Initializr metadata and the requested build tool; not for migrating or overwriting an existing application.
---

# Spring Boot 3 Scaffolding

Create a minimal application for the requested use case, not a fixed enterprise stack.
Target Boot 3; never silently switch major versions because a template is easier to obtain.

## Establish the generation contract

Confirm a new destination, project coordinates/package, Java level, Maven or Gradle and
required capabilities. Ask only for missing consequential choices. Preserve the requested
database, servlet/reactive stack, language and response contract. Do not add JPA, Redis,
Lombok, JWT issuance, AI providers or telemetry services without a requirement.

Read [the generation procedure](references/generation.md) before fetching or extracting an
archive. Record the approved provider, metadata timestamp and exact requested inputs.
A request to generate does not authorize publishing project details to an arbitrary provider.

## Version-specific checks

Select an advertised stable Boot 3 release and compatible Java/build-tool options.
Do not substitute Boot 4 when the public Initializr no longer advertises the requested
Boot 3 version. Offer an approved provider or a version-pinned manual build, clearly labeled
as an alternate path; do not downgrade a Boot 4 archive by merely changing its parent.
Keep Framework 6/Jackson 2 and Boot 3 test dependency conventions.

Never select an RC, milestone or snapshot unless explicitly requested. Version identifiers
must come from metadata; do not assume stable releases end with `.RELEASE`.
Check the effective build, including the JVM actually running the wrapper, before compiling.

## Build the smallest complete slice

Keep the Initializr wrapper, dependency management and source layout. Add one requested
feature slice with explicit validation, error behavior and a test for success and failure.
If no business feature was requested, keep the generated application and context test;
do not invent Order/User/payment entities. Add service credentials only as runtime inputs,
with disposable local test dependencies and no production defaults.

Add a minimal CI job matching the verified local command/JDK and required disposable
services. Use the repository's approved action versions and least privileges. Do not create
a deployment job, publish artifacts, install global tools or configure external accounts.
For local agent guidance, document real commands and constraints in AGENTS.md; Claude
guidance may point to that shared file. Do not invent tool permissions or install other clients.

## Verify and report

Run the wrapper build including the configured test lifecycle, then a bounded local startup
check if feasible. Bind only to loopback, select an unused port and clean up only processes
you start. Verify an actual response/readiness signal, not merely process creation.
Report files created, exact versions, command results and missing prerequisites.
Use the project's test policy; do not add arbitrary coverage thresholds.

[Good plan](examples/good-plan.md) and [bad plan](examples/bad-plan.md) illustrate scope.
This workflow is guidance, not a bundled generator or a guarantee that remote generation
will remain available.

## Official sources

- [Initializr metadata and generation](https://docs.spring.io/initializr/docs/current/reference/html/)
- [Boot 3 build systems](https://docs.spring.io/spring-boot/3.5/reference/using/build-systems.html)
- [Boot 3 testing](https://docs.spring.io/spring-boot/3.5/reference/testing/index.html)

## Gotchas

- Agent extracts into an existing checkout - use a new staging directory and refuse destination collisions.
- Agent hard-codes a stale Initializr version - inspect advertised capabilities and report unavailable versions.
- Agent generates both build tools or both web stacks - preserve the user's selected architecture.
- Agent declares completion after downloading a ZIP - verify the generated build, tests and startup separately.
