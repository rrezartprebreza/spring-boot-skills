# Spring Boot 4 Project Guidance

## Stack

- Spring Boot 4.x, Spring Framework 7, Java 21
- Jakarta EE 11, Jackson 3, and Boot 4 modular starters
- Build with the checked-in Maven wrapper

## Commands

- Build and verify: `./mvnw verify`
- Unit tests: `./mvnw test`
- Single test: `./mvnw -Dtest=ClassName test`

## Conventions

- Read neighboring classes and the dependency tree before generating code.
- Keep controllers thin, business rules in services, and persistence behind repositories.
- Use DTOs at HTTP and messaging boundaries.
- Use Boot 4 dedicated main and test starters and Jackson 3 `tools.jackson` APIs.
- Use `@MockitoBean`/`@MockitoSpyBean`; configure MockMvc or RestTestClient explicitly.
- Preserve existing error, security, pagination, nullability, and observability conventions.

## Skills

Use matching folders under `.claude/skills/`. Combine only the narrow skills required by the
change, including `spring-boot-migration` when upgrading an existing Boot 3 application.

## Completion

Run focused tests followed by `./mvnw verify`. Test the native executable when AOT behavior changes.
