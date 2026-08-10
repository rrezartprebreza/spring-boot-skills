# Spring Boot 3 Project Guidance

## Stack

- Spring Boot 3.x, Spring Framework 6, Java 21
- Build with the checked-in Maven wrapper
- Use Jakarta APIs and Jackson 2 conventions already present in the project

## Commands

- Build and verify: `./mvnw verify`
- Unit tests: `./mvnw test`
- Single test: `./mvnw -Dtest=ClassName test`

## Conventions

- Read neighboring classes before generating code.
- Keep controllers thin, business rules in services, and persistence behind repositories.
- Use DTOs at HTTP and messaging boundaries.
- Add immutable Flyway migrations for schema changes.
- Preserve existing response, error, security, pagination, and logging conventions.
- Use Testcontainers when behavior depends on a real database, cache, or broker.

## Skills

Use the matching folders under `.claude/skills/`. Combine narrow skills when a change crosses
concerns, such as REST plus JPA plus testing. Do not load unrelated skills.

## Completion

Run focused tests followed by `./mvnw verify`. Explain any verification that could not run.
