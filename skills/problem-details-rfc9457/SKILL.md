---
name: problem-details-rfc9457
description: >
  Use when implementing error handling, exception mappers, or error response formatting.
  Enforces RFC 9457 (Problem Details for HTTP APIs) using Spring's built-in ProblemDetail.
---

# Problem Details — RFC 9457

Spring Boot 3.x includes native RFC 9457 support via `ProblemDetail`.

## Enable in application.yml

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true  # enables Spring's built-in RFC 9457 handler
```

## ProblemDetail Response Shape

```json
{
  "type": "https://api.example.com/errors/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "No order found with id: 550e8400-e29b-41d4-a716-446655440000",
  "instance": "/api/v1/orders/550e8400-e29b-41d4-a716-446655440000",
  "errorCode": "ORDER_NOT_FOUND",
  "timestamp": "2026-04-13T10:00:00Z"
}
```

## Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://api.example.com/errors/not-found"));
        problem.setTitle("Resource Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "NOT_FOUND");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://api.example.com/errors/business-rule"));
        problem.setTitle("Business Rule Violation");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers,
        HttpStatusCode status, WebRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create("https://api.example.com/errors/validation"));
        problem.setTitle("Validation Failed");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("violations", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
            .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create("https://api.example.com/errors/internal"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        // Don't expose ex.getMessage() in production — log it instead
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return problem;
    }
}
```

## Custom Domain Exceptions

```java
// Base exception
public abstract class DomainException extends RuntimeException {
    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}

// Specific exceptions
public class OrderNotFoundException extends DomainException {
    public OrderNotFoundException(UUID orderId) {
        super("ORDER_NOT_FOUND", "Order not found: " + orderId);
    }
}

public class InsufficientInventoryException extends DomainException {
    public InsufficientInventoryException(UUID productId, int requested, int available) {
        super("INSUFFICIENT_INVENTORY",
            "Insufficient inventory for product %s: requested %d, available %d"
                .formatted(productId, requested, available));
    }
}
```

## Content Negotiation

Clients can request problem details explicitly with `Accept: application/problem+json`. Spring handles this automatically when `problemdetails.enabled: true` — your handler returns `ProblemDetail` and Spring sets the correct `Content-Type: application/problem+json`.

## ProblemDetail vs ApiResponse Envelope

| Use Case | Approach |
|---|---|
| Error responses only | ProblemDetail (RFC 9457) |
| All responses (success + error) | ApiResponse envelope |
| Public API with diverse clients | ProblemDetail — RFC standard |
| Internal microservices | Either — be consistent across services |

Choose one approach per project. Don't mix `ApiResponse` for success and `ProblemDetail` for errors — it confuses API consumers who see two different shapes.

## Gotchas
- Agent returns `Map<String, Object>` for errors — use `ProblemDetail`
- Agent exposes raw exception messages in 500 errors — log it, return generic message
- Agent uses custom error envelope alongside ProblemDetail — pick one standard
- Agent forgets to set `type` URI — required for RFC 9457 compliance
- Agent returns 200 with error in body — always use the correct HTTP status code
- Agent creates handler without extending `ResponseEntityExceptionHandler` — extend it to get Spring's built-in exception handling for free
