# Validation Layer Details

## Three-Layer Validation Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ Layer 1: SYNTACTIC (Controller/DTO)                             │
│ - Bean Validation (@NotNull, @Size, @Pattern, @Valid)           │
│ - Fails fast: 400 BAD REQUEST                                   │
│ - Field-level error messages                                    │
├─────────────────────────────────────────────────────────────────┤
│ Layer 2: BUSINESS (Service)                                     │
│ - Domain rules, invariants, cross-entity constraints            │
│ - Custom exceptions with codes: 422 UNPROCESSABLE ENTITY        │
│ - Reusable validators for complex rules                         │
├─────────────────────────────────────────────────────────────────┤
│ Layer 3: AUTHORIZATION (Service/Security)                       │
│ - Permissions, ownership, tenant isolation                      │
│ - 401 UNAUTHORIZED / 403 FORBIDDEN                              │
└─────────────────────────────────────────────────────────────────┘
```

## Layer 1: Syntactic Validation (DTO Bean Validation)

### Standard Annotations

```java
public record CreateTruckRequest(
    // Required string, not blank
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    // Required enum
    @NotNull(message = "Type is required")
    TruckType type,

    // Optional string with max length
    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    // Format validation
    @Pattern(regexp = "^[A-Z]{2}[0-9]{6}$", message = "Invalid license plate format")
    String licensePlate,

    // Numeric bounds
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 100000, message = "Capacity cannot exceed 100000")
    Integer capacity,

    // Email format
    @Email(message = "Invalid email format")
    String contactEmail,

    // Past/future dates
    @Future(message = "Expiry date must be in the future")
    LocalDate licenceExpiry,

    // Nested validation
    @Valid
    AddressDto address,

    // Collection validation
    @Size(min = 1, max = 10, message = "Must have 1-10 tags")
    @NotNull
    List<@NotBlank String> tags
) {}
```

### Validation Groups (Optional - for Partial Updates)

```java
public interface OnCreate {}
public interface OnUpdate {}

public record CreateTruckRequest(
    @NotBlank(groups = OnCreate.class)
    @Size(max = 100, groups = {OnCreate.class, OnUpdate.class})
    String name,
    ...
) {}

// Controller
@PostMapping
public ResponseEntity<TruckResponse> create(@Validated(OnCreate.class) @RequestBody CreateTruckRequest request) { ... }

@PatchMapping("/{id}")
public ResponseEntity<TruckResponse> patch(@PathVariable Long id, @Validated(OnUpdate.class) @RequestBody CreateTruckRequest request) { ... }
```

### Controller Validation Setup

```java
@RestController
@RequestMapping("/api/trucks")
@Validated  // Enables method-level validation on params
@RequiredArgsConstructor
public class TruckController {
    private final TruckService truckService;

    @PostMapping
    public ResponseEntity<TruckResponse> create(@Valid @RequestBody CreateTruckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(truckService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TruckResponse> get(@PathVariable @Min(1) Long id) {
        return ResponseEntity.ok(truckService.getById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TruckListResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        ...
    }
}
```

### Global Validation Handler

```java
@RestControllerAdvice
@Slf4j
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldErrorDto(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
            .map(cv -> new FieldErrorDto(cv.getPropertyPath().toString(), cv.getMessage()))
            .toList();
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", errors);
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String code, String msg, List<FieldErrorDto> errors) {
        log.warn("Validation failed: {} - {}", code, errors);
        return ResponseEntity.status(status).body(
            new ErrorResponse(code, msg, errors, MDC.get("traceId"), Instant.now())
        );
    }
}
```

## Layer 2: Business Validation (Service)

### Custom Exceptions

```java
public class BusinessRuleViolationException extends RuntimeException {
    private final String code;
    private final Map<String, Object> details = new HashMap<>();

    public BusinessRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessRuleViolationException addDetail(String key, Object value) {
        details.put(key, value);
        return this;
    }

    // Getters
}

// Predefined codes
public class BusinessErrors {
    public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
    public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";
    public static final String RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED";
    public static final String PRECONDITION_FAILED = "PRECONDITION_FAILED";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
}
```

### Service-Level Validation

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TruckService {
    private final TruckRepository truckRepository;
    private final DriverRepository driverRepository;

    @Transactional
    public TruckResponse create(CreateTruckRequest request) {
        // Cross-field / database validation
        validateUniqueLicensePlate(request.licensePlate());
        validateCapacity(request.capacity());

        Truck truck = truckMapper.toEntity(request);
        enrichForCreation(truck);
        return truckMapper.toResponse(truckRepository.save(truck));
    }

    @Transactional
    public TruckResponse updateStatus(Long id, TruckStatus newStatus) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));

        // State machine validation
        validateStateTransition(truck.getStatus(), newStatus);

        truck.setStatus(newStatus);
        return truckMapper.toResponse(truck);
    }

    // Validation helpers
    private void validateUniqueLicensePlate(String licensePlate) {
        if (truckRepository.existsByLicensePlateAndTenantId(licensePlate, tenantId)) {
            throw new BusinessRuleViolationException(
                BusinessErrors.DUPLICATE_RESOURCE,
                "Truck with license plate '" + licensePlate + "' already exists"
            ).addDetail("field", "licensePlate").addDetail("value", licensePlate);
        }
    }

    private void validateCapacity(Integer capacity) {
        if (capacity != null && capacity > TruckConstants.MAX_CAPACITY_KG) {
            throw new BusinessRuleViolationException(
                BusinessErrors.PRECONDITION_FAILED,
                "Capacity exceeds maximum allowed: " + TruckConstants.MAX_CAPACITY_KG
            ).addDetail("maxCapacity", TruckConstants.MAX_CAPACITY_KG);
        }
    }

    private void validateStateTransition(TruckStatus current, TruckStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleViolationException(
                BusinessErrors.INVALID_STATE_TRANSITION,
                String.format("Cannot transition from %s to %s", current, target)
            ).addDetail("from", current).addDetail("to", target);
        }
    }
}
```

### Reusable Custom Validators

```java
// Annotation
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueLicensePlateValidator.class)
@Documented
public @interface UniqueLicensePlate {
    String message() default "License plate already exists in this tenant";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Validator
@Component
@RequiredArgsConstructor
public class UniqueLicensePlateValidator implements ConstraintValidator<UniqueLicensePlate, String> {
    private final TruckRepository truckRepository;
    private final TenantContext tenantContext;

    @Override
    public boolean isValid(String licensePlate, ConstraintValidatorContext context) {
        if (licensePlate == null) return true;  // @NotNull handles null
        return !truckRepository.existsByLicensePlateAndTenantId(licensePlate, tenantContext.getTenantId());
    }
}

// Usage on DTO
public record CreateTruckRequest(
    @UniqueLicensePlate
    String licensePlate,
    ...
) {}
```

## Layer 3: Authorization Validation

### Permission-Based
```java
@Service
@RequiredArgsConstructor
public class AuthorizationService {
    private final PermissionEvaluator permissionEvaluator;

    public void requirePermission(Permission permission, Object resource) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!permissionEvaluator.hasPermission(auth, resource, permission)) {
            throw new AccessDeniedException("Missing permission: " + permission.name());
        }
    }

    public void requireTenantAccess(Long resourceTenantId) {
        Long currentTenantId = TenantContext.getTenantId();
        if (!currentTenantId.equals(resourceTenantId)) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }
    }
}
```

### Ownership Checks
```java
@Service
@RequiredArgsConstructor
public class TruckService {
    private final AuthorizationService authService;

    @Transactional
    public void delete(Long id) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));

        // Resource-level authorization
        authService.requirePermission(Permission.TRUCK_DELETE, truck);
        
        // Or ownership-based
        authService.requireOwnershipOrAdmin(truck.getCreatedBy());

        truckRepository.delete(truck);
    }
}
```

## Validation Flow Summary

| Input Type | Validator | Layer | Error Code | HTTP Status |
|------------|-----------|-------|------------|-------------|
| Request body | Bean Validation | Controller | `VALIDATION_ERROR` | 400 |
| Path variable | `@Min`, `@Max` | Controller | `VALIDATION_ERROR` | 400 |
| Query param | `@RequestParam` constraints | Controller | `VALIDATION_ERROR` | 400 |
| Cross-field DTO | Custom `@Valid` validator | Controller | `VALIDATION_ERROR` | 400 |
| Unique constraint | Service check | Service | `DUPLICATE_RESOURCE` | 409 |
| State machine | Service logic | Service | `INVALID_STATE_TRANSITION` | 422 |
| Business rule | Service logic | Service | Custom codes | 422/409 |
| Permissions | Auth service | Service | `FORBIDDEN` | 403 |
| Tenant isolation | Repository + Auth | Service/Repo | `NOT_FOUND` | 404 |

## Testing Validation

```java
@WebMvcTest(TruckController.class)
class TruckValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean TruckService truckService;

    @Test
    void shouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/api/trucks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"type\":\"FLATBED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[?(@.field=='name')].message").value("Name is required"));
    }

    @Test
    void shouldRejectInvalidLicensePlate() throws Exception {
        mockMvc.perform(post("/api/trucks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"type\":\"FLATBED\",\"licensePlate\":\"invalid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field=='licensePlate')].message").value("Invalid license plate format"));
    }

    @Test
    void shouldRejectDuplicateLicensePlate() throws Exception {
        when(truckService.create(any())).thenThrow(
            new BusinessRuleViolationException("DUPLICATE_RESOURCE", "Already exists"));

        mockMvc.perform(post("/api/trucks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"type\":\"FLATBED\",\"licensePlate\":\"ABC123\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }
}
```