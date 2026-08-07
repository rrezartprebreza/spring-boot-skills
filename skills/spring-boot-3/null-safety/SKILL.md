---
name: null-safety
description: >
  Use when annotating nullability in Spring Boot 3 / Spring Framework 6 code, integrating Kotlin,
  or adding static nullability checks without assuming Spring Framework 7 JSpecify defaults.
---

# Null Safety (Boot 3 / Framework 6)

Use the nullability model supported by the project. Spring Framework 6 commonly uses
`org.springframework.lang.Nullable`, `@NonNull`, and package-level `@NonNullApi`. JSpecify can be
introduced deliberately, but do not assume Framework 7 migration semantics in a Boot 3 module.

```java
@NonNullApi
package com.example.orders;

import org.springframework.lang.NonNullApi;
```

Annotate genuine nullable results and parameters with `@Nullable`. Keep repository return types
explicit, validate external input at boundaries, and use Kotlin compiler settings that match the
annotations when Java and Kotlin are mixed. If the project standardizes JSpecify independently,
apply it consistently at module boundaries and document the chosen checker.

## Gotchas

- Agent assumes Framework 7 JSpecify defaults - Boot 3 projects usually use Spring's legacy annotations.
- Agent uses `@NonNullApi` without importing the package annotation - package metadata must compile and be checked in.
- Agent marks every value `@NonNull` - establish a package default and annotate only nullable exceptions.
- Agent treats annotations as runtime validation - use Bean Validation or explicit checks for input validation.
- Agent changes a repository's nullable contract without updating callers - preserve `Optional`, nullable, and empty collection semantics.
- Agent mixes JSpecify and Spring annotations without a migration plan - choose one module boundary policy.
