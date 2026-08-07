---
name: api-versioning
description: >
  Use when versioning Spring MVC or WebFlux APIs in Spring Boot 3 / Spring Framework 6. Covers
  explicit URL, header, and media-type strategies, compatibility rules, and deprecation handling.
---

# API Versioning (Boot 3 / Framework 6)

Boot 3 does not provide Boot 4's built-in mapping `version` attribute. Choose one explicit
strategy and keep it consistent: URL segments, a request header, or media-type parameters.

## Prefer a visible, testable contract

For a public API, `/api/v1/orders` and `/api/v2/orders` are usually the clearest choice. If URLs
must remain stable, resolve a request header at the web boundary and route to versioned handlers.
Do not hide version selection in business services or duplicate it across filters and controllers.

```java
@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller {
    @GetMapping("/{id}")
    OrderV1 get(@PathVariable UUID id) { ... }
}
```

Document supported versions in OpenAPI, define a removal policy, and return a consistent error for
unsupported versions. Add `Deprecation`, `Sunset`, and `Link` headers through a response advice or
filter when retiring a version.

## Migration boundary

Keep version-specific DTOs and controllers thin. Map both versions to the same application use
case, and do not copy domain logic into each version. Add contract tests for every supported
version and verify content negotiation if using media types.

## Gotchas

- Agent uses Boot 4's `version` attribute on Boot 3 - it is not available; choose an explicit routing strategy.
- Agent mixes URL, header, and media-type resolution - select one source of truth.
- Agent puts version branching in services - keep compatibility at the web adapter boundary.
- Agent changes response fields without versioning - preserve old contracts or publish a new version.
- Agent invents deprecation headers per controller - centralize them in advice or a filter.
- Agent documents only the latest version - keep OpenAPI and contract tests for every supported version.
