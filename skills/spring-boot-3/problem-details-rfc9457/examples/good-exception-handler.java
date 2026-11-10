// GOOD — extends ResponseEntityExceptionHandler, maps the actual domain exception hierarchy, custom properties

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_BASE_URI = "https://api.example.com/errors/";

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URI + ex.getErrorCode().toLowerCase(java.util.Locale.ROOT)));
        problem.setTitle(ex.getHttpStatus().getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ex.getErrorCode());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create(ERROR_BASE_URI + "validation"));
        problem.setTitle("Validation Failed");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("violations", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage() == null ? "Invalid value" : e.getDefaultMessage()))
            .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    // @PreAuthorize denials are thrown inside the dispatcher, so the filter chain's
    // AccessDeniedHandler never sees them — without this the catch-all returns 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        problem.setType(URI.create(ERROR_BASE_URI + "forbidden"));
        problem.setTitle("Forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create(ERROR_BASE_URI + "internal"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return problem;
    }
}
