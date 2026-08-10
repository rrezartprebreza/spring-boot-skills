# Spring Boot 3 Project Guidance

## Stack

- Spring Boot 3.x, Spring Framework 6, Java 21
- Maven wrapper: `./mvnw`
- Jakarta APIs; do not introduce `javax.*` imports
- Jackson 2 unless the project explicitly configures another serializer

## Commands

- Build: `./mvnw verify`
- Unit tests: `./mvnw test`
- Single test: `./mvnw -Dtest=ClassName test`
- Format or lint: use the plugins already configured in `pom.xml`

## Engineering Rules

- Inspect neighboring code before choosing package structure or abstractions.
- Keep controllers as HTTP adapters and transactions in application services.
- Use request/response DTOs; do not expose persistence entities as API contracts.
- Add Flyway migrations for schema changes and never edit an applied migration.
- Preserve the existing error, pagination, security, and observability contracts.
- Prefer focused tests; use Testcontainers for database and broker integration.

## Skills

Load relevant skills from `.codex/skills/` before changing Spring code. Typical combinations:

- Endpoint: `rest-api-conventions`, `layered-architecture`, `testing-pyramid`
- Persistence: `spring-data-jpa`, `transactional-patterns`, `flyway-migrations`
- Security: `oauth2-resource-server` or `spring-security-jwt`
- Messaging: `event-driven-messaging`

## Verification

Run the narrowest relevant test first, then `./mvnw verify` before handing off. Report any test
that could not run and do not hide unrelated failures.
