---
name: spring-boot-migration
description: >
  Use when preparing a Spring Boot 3 application for Boot 4, upgrading to Boot 3.5 first,
  removing deprecated APIs, auditing dependencies, or planning a staged Framework 7 migration.
---

# Spring Boot 3 to 4 Migration

Migrate in controlled stages. Keep behavior changes separate from the framework upgrade.

## Establish the baseline

1. Upgrade to the latest Boot 3.5 maintenance release.
2. Run unit, slice, integration, startup, and migration tests before changing the major version.
3. Remove Boot 3 deprecations and record all explicitly versioned dependencies.
4. Compare the Boot 3.5 and Boot 4 dependency-management reports.
5. Verify Spring Cloud and other portfolio release-train compatibility independently.

## Prepare for Boot 4

- Require Java 17 or newer; prefer Java 21 for application builds.
- Replace `javax.*` remnants with Jakarta APIs before the upgrade.
- Remove Undertow assumptions; Boot 4 requires a Servlet 6.1-compatible container.
- Inventory Jackson 2 custom modules, serializers, `ObjectMapper` beans, and package imports.
- Inventory test annotations, especially `@MockBean`, `@SpyBean`, and implicit MockMvc setup.
- Add `spring-boot-properties-migrator` temporarily after changing the Boot version, then remove it.

## Migrate dependencies deliberately

Boot 4 is more modular. Prefer dedicated starters over relying on incidental transitive dependencies.
Expect dedicated starters for web MVC, security tests, Flyway/Liquibase, and technology-specific tests.
Use the classic starters only as a temporary diagnostic bridge, never as the final dependency model.

## Verify the result

- Run the application with every supported profile.
- Exercise schema migration against a production-like database copy.
- Verify JSON contracts, security failures, pagination, and error responses.
- Confirm actuator exposure, logging, metrics, and tracing behavior.
- Remove the properties migrator and all classic starters before declaring the migration complete.

## Examples

- See `examples/good-migration-plan.md` for a staged migration.
- See `examples/bad-migration-plan.md` for a risky one-step upgrade.

## Gotchas

- Agent jumps from an old Boot 3 minor directly to Boot 4 - upgrade to current 3.5 first.
- Agent changes framework versions and business behavior together - isolate the migration diff.
- Agent assumes all Boot 3 starters keep the same names and transitive dependencies - audit each one.
- Agent leaves `spring-boot-properties-migrator` in production - remove it after configuration cleanup.
- Agent treats passing compilation as completion - verify runtime wiring, JSON, security, and tests.
