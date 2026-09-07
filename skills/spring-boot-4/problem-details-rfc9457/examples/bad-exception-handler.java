// ❌ BAD — returns Map, exposes internals, wrong status codes

@RestControllerAdvice
public class GlobalExceptionHandler {                      // no framework error handling shown

    @ExceptionHandler(OrderNotFoundException.class)
    public Map<String, Object> handleNotFound(OrderNotFoundException ex) {
        return Map.of(                                     // returns Map, not ProblemDetail
            "error", ex.getMessage(),
            "status", 404
        );                                                 // body says 404 but HTTP status remains 200
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessRule(BusinessRuleViolationException ex) {
        return ResponseEntity.ok(                          // returns 200 with error body!
            Map.of("error", ex.getMessage()));             // should be 422 Unprocessable Entity
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(500).body(Map.of(
            "error", ex.getMessage(),                      // exposes raw exception message (security risk)
            "stackTrace", Arrays.toString(ex.getStackTrace())  // NEVER expose stack traces
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
            .body("Validation failed: " + ex.getMessage());  // raw string, no structure
    }
}
