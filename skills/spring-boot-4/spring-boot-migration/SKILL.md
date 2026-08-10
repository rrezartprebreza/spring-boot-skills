---
name: spring-boot-migration
description: >
  Use when upgrading an application from Spring Boot 3.5 to Boot 4, completing a Framework 7
  migration, replacing modular starters, adopting Jackson 3, or diagnosing post-upgrade failures.
---

# Spring Boot 4 Migration

Migrate from the latest Boot 3.5 release and keep business changes outside the upgrade diff.

## Required baseline

- Use Java 17 or newer; prefer Java 21 for application builds.
- Use Spring Framework 7, Jakarta EE 11, and a Servlet 6.1-compatible container.
- Upgrade Kotlin to the supported baseline when applicable.
- Use a Boot 4-compatible Spring Cloud release train.
- Use GraalVM 25 or newer for native images.

## Migrate modules and starters

Boot 4 splits infrastructure into focused modules and starters.

- Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc`.
- Use dedicated technology starters such as `spring-boot-starter-flyway`.
- Use technology-specific test starters; they bring the core test starter transitively.
- Use `spring-boot-starter-security-test` for Spring Security test support.
- Use `spring-boot-starter-batch-jdbc` when jobs require persistent metadata and restartability.
- Use classic starters only as a temporary migration bridge, then remove them.

## Migrate code

- Replace Jackson 2 `com.fasterxml.jackson` customization with Jackson 3 `tools.jackson` APIs.
- Replace removed `@MockBean` and `@SpyBean` with framework Mockito bean overrides.
- Add `@AutoConfigureMockMvc` when a `@SpringBootTest` requires MockMvc.
- Replace removed or moved Boot package imports and deleted Boot 3 deprecations.
- Remove Undertow configuration and verify the selected server supports Servlet 6.1.

## Configuration workflow

1. Add `spring-boot-properties-migrator` temporarily.
2. Start every supported profile and fix each reported property.
3. Remove the migrator before release.
4. Compare `/actuator/configprops`, logging, JSON, and endpoint behavior against the baseline.

## Verification

- Run unit, slice, integration, native, startup, and database migration tests as applicable.
- Verify JSON contracts, security failure responses, actuator exposure, and test slice composition.
- Inspect the dependency tree for old starters, duplicate Jackson generations, and unmanaged versions.

## Examples

- See `examples/good-migration-plan.md` and `examples/bad-migration-plan.md`.

## Gotchas

- Agent keeps old starter names because compilation succeeds transitively - use Boot 4 dedicated starters.
- Agent adds both a technology test starter and the classic test starter - avoid duplicate test graphs.
- Agent leaves Jackson 2 and Jackson 3 customizations together - migrate the complete JSON boundary.
- Agent expects plain Batch starter metadata to persist - use the JDBC starter when restartability matters.
- Agent leaves the properties migrator or classic starters installed - remove temporary migration aids.
