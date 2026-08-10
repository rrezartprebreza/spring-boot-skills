# Spring Boot 4 Project Guidance

## Stack

- Spring Boot 4.x, Spring Framework 7, Java 21
- Maven wrapper: `./mvnw`
- Jakarta EE 11, Jackson 3, and Boot 4 modular starters
- Use dedicated technology test starters rather than the classic test starter

## Commands

- Build: `./mvnw verify`
- Unit tests: `./mvnw test`
- Single test: `./mvnw -Dtest=ClassName test`
- Native tests: use the configured native profile; do not invent one

## Engineering Rules

- Inspect neighboring code and the dependency tree before choosing an API or starter.
- Keep controllers as adapters and transactions in application services.
- Use DTOs; do not expose Hibernate entities as external contracts.
- Use `tools.jackson` APIs for Jackson customization.
- Use `@MockitoBean`/`@MockitoSpyBean` and explicit test auto-configuration.
- Add Flyway migrations for schema changes and never edit applied migrations.
- Preserve error, security, pagination, nullability, and observability contracts.

## Skills

Load relevant skills from `.codex/skills/`. Typical combinations:

- Upgrade: `spring-boot-migration`, `testing-pyramid`
- Endpoint: `rest-api-conventions`, `api-versioning`, `testing-pyramid`
- Persistence: `spring-data-jpa`, `transactional-patterns`, `flyway-migrations`
- Production: `production-observability`, `container-native-deployment`

## Verification

Run focused tests and then `./mvnw verify`. For native changes, test the produced native executable.
Report any verification that could not run.
