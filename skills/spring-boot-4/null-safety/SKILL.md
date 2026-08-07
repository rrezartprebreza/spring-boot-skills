---
name: null-safety
description: >
  Use when annotating nullability in Spring Boot 4 / Spring Framework 7, migrating to JSpecify,
  integrating Kotlin, or enabling build-time null checks with NullAway.
---

# Null Safety with JSpecify (Boot 4)

Spring Framework 7 adopts JSpecify for nullability. Use `org.jspecify.annotations` for new code
and migrate away from the older Spring nullability annotations at module boundaries.

## Mark packages and annotate exceptions

```java
@NullMarked
package com.example.orders;

import org.jspecify.annotations.NullMarked;
```

Inside a null-marked package, types are non-null by default. Annotate only genuine exceptions:

```java
@Nullable
Order findActive(String customerEmail) { ... }

List<@Nullable String> namesWithGaps; // non-null list, nullable elements
@Nullable List<String> maybeNoList;   // nullable list, non-null elements
```

Use `Object @Nullable []` for a nullable array and `@Nullable Object[]` for a non-null array whose
elements may be null. Add the JSpecify dependency through the Boot dependency management.

JSpecify is metadata. IntelliJ can inspect it, while NullAway with `JSpecifyMode=true` can enforce
the contract during compilation. Kotlin consumes the annotations as real nullability.

## Gotchas

- Agent uses `org.springframework.lang.Nullable` in new Framework 7 code - prefer JSpecify.
- Agent annotates every parameter with `@NonNull` - use package-level `@NullMarked` and mark exceptions.
- Agent writes `@Nullable List<String>` for nullable elements - use `List<@Nullable String>`.
- Agent puts `@Nullable` on the wrong side of an array type - distinguish nullable arrays from nullable elements.
- Agent expects annotations alone to fail the build - add NullAway or enable IDE inspections.
- Agent forgets `package-info.java` - without `@NullMarked`, most checks remain unknown.
- Agent mixes JSpecify and legacy Spring annotations in one module - migrate the module consistently.
