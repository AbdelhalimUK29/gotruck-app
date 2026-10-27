# Centralized Error Handling

## Core Principle

**Use a global `@RestControllerAdvice` for consistent error responses.** Never scatter try/catch blocks in controllers.

## Exception Hierarchy

```java
// Base exception with error code
public abstract class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    protected ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    // Getters
}

// 4xx - Client Errors
public class ValidationException extends ApiException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, message);
    }
}

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resource, Object id) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, 
            String.format("%s not found: %s", resource, id));
    }
}

public class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String resource, String field, Object value) {
        super("DUPLICATE_RESOURCE", HttpStatus.CONFLICT,
            String.format("%s with %s '%s' already exists", resource, field, value));
    }
}

public class BusinessRuleViolationException extends ApiException {
    public BusinessRuleViolationException(String code, String message) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}

public class InvalidStateTransitionException extends BusinessRuleViolationException {
    public InvalidStateTransitionException(String entity, String from, String to) {
        super("INVALID_STATE_TRANSITION",
            String.format("Cannot transition %s from %s to %s", entity, from, to));
    }
}

// 401/403 - Security
public class AuthenticationRequiredException extends ApiException {
    public AuthenticationRequiredException() {
        super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}

public class AccessDeniedException extends ApiException {
    public AccessDeniedException(String message) {
        super("FORBIDDEN", HttpStatus.FORBIDDEN, message);
    }
}

// 5xx - Server Errors
public class ExternalServiceException extends ApiException {
    public ExternalServiceException(String service, String message) {
        super("EXTERNAL_SERVICE_ERROR", HttpStatus.BAD_GATEWAY,
            String.format("External service '%s' error: %s", service, message));
    }
}
```

## Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Validation errors (DTO @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldErrorDto(fe.getField(), fe.getDefaultMessage()))
            .toList();
        
        log.warn("Validation failed: {}", errors);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", errors);
    }

    // Path/query param validation (@Validated on controller)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
            .map(cv -> new FieldErrorDto(cv.getPropertyPath().toString(), cv.getMessage()))
            .toList();
        
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", errors);
    }

    // Our custom API exceptions
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("API error [{}]: {}", ex.getCode(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of());
    }

    // Spring Security
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", List.of());
    }

    // JPA / Database
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Database integrity violation", ex);
        
        // Check for unique constraint violations
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("unique") || message.contains("duplicate")) {
            return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", 
                "A resource with this value already exists", List.of());
        }
        
        return buildResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", 
            "Database constraint violation", List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_FAILURE", 
            "Resource was modified by another request. Please retry.", List.of());
    }

    // Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);  // Full stack trace in logs
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", 
            "An unexpected error occurred", List.of());
    }

    // Helper
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, 
                                                        String message, List<FieldErrorDto> errors) {
        String traceId = MDC.get("traceId");  // From logging context
        return ResponseEntity.status(status)
            .body(new ErrorResponse(code, message, errors, traceId, Instant.now()));
    }
}
```

## Consistent Error Response Format

```java
public record ErrorResponse(
    String code,                    // Machine-readable: "VALIDATION_ERROR", "NOT_FOUND"
    String message,                 // Human-readable: "Invalid request"
    List<FieldErrorDto> errors,     // Field-level details (empty for non-validation)
    String traceId,                 // Correlation ID for debugging
    Instant timestamp               // ISO-8601
) {}

public record FieldErrorDto(String field, String message) {}
```

### Example Responses

**400 Validation Error**
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request",
  "errors": [
    {"field": "name", "message": "Name is required"},
    {"field": "licensePlate", "message": "Invalid license plate format"}
  ],
  "traceId": "abc-123-def",
  "timestamp": "2026-09-25T10:30:00Z"
}
```

**404 Not Found**
```json
{
  "code": "NOT_FOUND",
  "message": "Truck not found: 123",
  "errors": [],
  "traceId": "abc-123-def",
  "timestamp": "2026-09-25T10:30:00Z"
}
```

**409 Conflict**
```json
{
  "code": "DUPLICATE_RESOURCE",
  "message": "Truck with licensePlate 'ABC123' already exists",
  "errors": [],
  "traceId": "abc-123-def",
  "timestamp": "2026-09-25T10:30:00Z"
}
```

**422 Business Rule Violation**
```json
{
  "code": "INVALID_STATE_TRANSITION",
  "message": "Cannot transition Truck from DELIVERED to ACTIVE",
  "errors": [],
  "traceId": "abc-123-def",
  "timestamp": "2026-09-25T10:30:00Z"
}
```

**500 Internal Error**
```json
{
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "errors": [],
  "traceId": "abc-123-def",
  "timestamp": "2026-09-25T10:30:00Z"
}
```

## HTTP Status Code Mapping

| Code | HTTP Status | When to Use |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request body/query/path invalid |
| `UNAUTHORIZED` | 401 | Missing/invalid token |
| `FORBIDDEN` | 403 | Authenticated but not authorized |
| `NOT_FOUND` | 404 | Resource doesn't exist |
| `DUPLICATE_RESOURCE` | 409 | Unique constraint violation |
| `INVALID_STATE_TRANSITION` | 422 | Business rule prevents action |
| `OPTIMISTIC_LOCK_FAILURE` | 409 | Concurrent modification |
| `EXTERNAL_SERVICE_ERROR` | 502 | Downstream service failure |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Best Practices

| Practice | Why |
|----------|-----|
| Catch specific exceptions first | Most specific handler wins |
| Log at appropriate level | WARN for client errors, ERROR for server errors |
| Include traceId | Correlate logs across services |
| Never expose stack traces | Security: no internal details to clients |
| Use problem details (RFC 7807) | Consider `application/problem+json` for standardization |
| Test exception handlers | Verify correct status codes and response format |

## Testing Error Handling

```java
@WebMvcTest(TruckController.class)
class TruckControllerErrorHandlingTest {

    @Autowired MockMvc mockMvc;
    @MockBean TruckService truckService;

    @Test
    void shouldReturn400ForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/trucks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))  // Missing required fields
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void shouldReturn404WhenNotFound() throws Exception {
        when(truckService.getById(999L)).thenThrow(new ResourceNotFoundException("Truck", 999L));
        
        mockMvc.perform(get("/api/trucks/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenForbidden() throws Exception {
        when(truckService.getById(1L)).thenThrow(new AccessDeniedException("No access"));
        
        mockMvc.perform(get("/api/trucks/1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
```