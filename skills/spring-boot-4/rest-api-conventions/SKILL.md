---
name: rest-api-conventions
description: >
  Use when generating REST controllers, response wrappers, DTOs, error handlers, or any
  HTTP-facing code. Defines response envelope, HTTP status mapping, pagination, and API
  versioning (including Spring Boot 4's native version routing).
---

# REST API Conventions

## Response Envelope

All endpoints return a consistent envelope:

```json
{
  "success": true,
  "data": { },
  "error": null,
  "timestamp": "2026-04-13T10:00:00Z"
}
```

Error response:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ORDER_NOT_FOUND",
    "message": "Order with id 123 not found",
    "details": []
  },
  "timestamp": "2026-04-13T10:00:00Z"
}
```

## ApiResponse Wrapper

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, List.of()), Instant.now());
    }
}

public record ApiError(String code, String message, List<String> details) {}
```

## HTTP Status Mapping

| Scenario | Status |
|----------|--------|
| GET — found | 200 |
| POST — created resource | 201 |
| PUT/PATCH — updated | 200 |
| DELETE — deleted | 204 (no body) |
| Validation failure | 400 |
| Unauthenticated | 401 |
| Forbidden | 403 |
| Not found | 404 |
| Conflict (duplicate) | 409 |
| Unhandled server error | 500 |

## URL Conventions

- **Plural nouns** for resources: `/orders`, `/users`, `/products`
- **Kebab-case** for multi-word: `/order-items`, not `/orderItems`
- **Versioning in path**: `/api/v1/orders` — route with native API versioning (below), don't duplicate controllers per version
- **Nested resources** max 2 levels: `/orders/{id}/items` ✅, `/orders/{id}/items/{itemId}/notes` ❌ — flatten to `/order-item-notes/{id}`
- **IDs as UUIDs** in path, never auto-increment integers exposed in URL

```
GET    /api/v1/orders              → list (paginated)
POST   /api/v1/orders              → create
GET    /api/v1/orders/{id}         → get one
PUT    /api/v1/orders/{id}         → full update
PATCH  /api/v1/orders/{id}         → partial update
DELETE /api/v1/orders/{id}         → delete
GET    /api/v1/orders/{id}/items   → nested resource
```

## API Versioning (Native in Boot 4)

Spring Boot 4 / Framework 7 route requests by API version natively — never hand-roll it
with duplicated `V1`/`V2` controllers, custom `RequestCondition`s, or header `if` checks.

Pick ONE resolution strategy per API (path segment, header, query param, or media-type param):

```yaml
spring:
  mvc:                        # WebFlux: same keys under spring.webflux.apiversion.*
    apiversion:
      use:
        path-segment: 1       # index of the path segment holding the version: /api/v1.1/orders
        # header: X-API-Version
        # query-parameter: version
      supported: [1.0, 1.1, 2.0]
      default: 1.0
```

Route with the `version` attribute on any mapping annotation:

```java
@GetMapping("/{id}")                            // no version — matches any
public OrderResponse getById(@PathVariable UUID id) { ... }

@GetMapping(value = "/{id}", version = "1.1")   // fixed: matches 1.1 only
public OrderResponseV1_1 getByIdV1_1(@PathVariable UUID id) { ... }

@GetMapping(value = "/{id}", version = "1.2+")  // baseline: 1.2 and supported versions above
public OrderResponseV2 getByIdV2(@PathVariable UUID id) { ... }
```

The most specific matching version wins. Unsupported version → 400 (`InvalidApiVersionException`);
missing required version → 400 (`MissingApiVersionException`).

- **Deprecate old versions** with `StandardApiVersionDeprecationHandler` (register via
  `WebMvcConfigurer#configureApiVersioning(ApiVersionConfigurer)`) — it emits RFC 9745
  `Deprecation`/`Sunset` and `Link` response headers
- **Clients**: `RestClient`/`WebClient` and HTTP interface clients send versions too —
  configure `.apiVersionInserter(ApiVersionInserter.fromHeader("X-API-Version").build())`
  and `.defaultVersion("1.2")` on the builder, matching the server's strategy

## Pagination

```json
{
  "success": true,
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "last": false
  }
}
```

Query params: `?page=0&size=20&sort=createdAt,desc`

Use Spring Data `Pageable` in controllers:
```java
@GetMapping
public ApiResponse<Page<OrderResponse>> list(Pageable pageable) {
    return ApiResponse.ok(orderService.findAll(pageable).map(OrderResponse::from));
}
```

**Cap the page size.** A bare `Pageable` accepts `?size=100000` from any client — one request can
drag your whole table into memory. Spring's default cap is 2000, still too high for most APIs:

```yaml
spring:
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100   # requests above this are silently clamped
```

## Global Exception Handler

```java
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList();
        return ResponseEntity.status(400)
            .body(new ApiResponse<>(false, null, new ApiError("VALIDATION_FAILED", "Invalid input", details), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(500).body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

## Gotchas
- Agent returns raw objects without envelope — always wrap in `ApiResponse.ok(...)`
- Agent uses `ResponseEntity<Map<String, Object>>` for errors — use `ApiResponse`
- Agent puts exception handlers in controllers — always use `@RestControllerAdvice`
- Agent uses `Long` IDs in URLs — use `UUID`
- Agent accepts unbounded `Pageable` — set `spring.data.web.pageable.max-page-size` or one request can pull the whole table
- Agent returns `Page<Entity>` serialized directly — exposes Hibernate internals; map to DTOs first
- Agent hand-rolls versioning with duplicated `/v1`/`/v2` controllers — Boot 4 has native API versioning: `version` attribute on mappings + `spring.mvc.apiversion.*`
- Agent adds `spring-boot-starter-web` — renamed `spring-boot-starter-webmvc` in Boot 4 (MockMvc tests: `spring-boot-starter-webmvc-test`)
- Agent uses `@JsonComponent` or `Jackson2ObjectMapperBuilderCustomizer` to tune serialization — Jackson 3 renames: `@JacksonComponent`, `JsonMapperBuilderCustomizer`; declare `JsonMapper` beans, not generic `ObjectMapper`
- Agent tests controllers with bare `@SpringBootTest` expecting MockMvc — Boot 4 no longer auto-provides it; add `@AutoConfigureMockMvc` (or the new `RestTestClient` via `@AutoConfigureRestTestClient`)
