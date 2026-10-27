# API Response Design

## Core Principle

**Responses are stable, intentional contracts — not database dumps.** Every field must have a purpose.

## Response Structure

### Collection Endpoints
```java
// Standard paginated response
public record PageResponse<T>(
    List<T> content,
    PageMetadata metadata
) {}

public record PageMetadata(
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    SortMetadata sort
) {}

public record SortMetadata(
    List<SortField> fields
) {}

public record SortField(
    String property,
    String direction  // "asc" or "desc"
) {}
```

```java
@GetMapping("/trucks")
public ResponseEntity<PageResponse<TruckListResponse>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        TruckFilterRequest filter) {
    
    Pageable pageable = PageRequest.of(page, size, parseSort(sort));
    Page<TruckListResponse> result = truckService.list(filter, pageable);
    
    return ResponseEntity.ok(PageResponse.of(result));
}
```

### Single Resource
```java
// Direct response object
public record TruckResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    String description,
    AddressDto address,
    DriverSummaryDto driver,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// Response
@GetMapping("/trucks/{id}")
public ResponseEntity<TruckResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(truckService.getById(id));
}
```

### Error Response (Consistent)
```java
// Always same structure
public record ErrorResponse(
    String code,
    String message,
    List<FieldErrorDto> errors,
    String traceId,
    Instant timestamp
) {}
```

## Response Design Rules

### 1. Stable Field Names
```java
// ✅ Stable - won't break clients
public record TruckResponse(
    Long id,
    String name,
    TruckStatus status
) {}

// ❌ Unstable - tied to DB column names that change
public record TruckResponse(
    Long truckId,
    String truckName,
    String truckStatus
) {}
```

### 2. No Internal Fields
| Exclude | Reason |
|---------|--------|
| `tenantId` | Security: tenant isolation |
| `createdBy`, `updatedBy` | Internal audit |
| `version` | Optimistic locking implementation detail |
| `deleted`, `deletedAt` | Soft delete internals |
| `passwordHash`, `apiKey` | Secrets |
| `isAdmin`, `permissions` | Authorization internals |

### 3. Denormalize for Client Convenience
```java
// Instead of forcing client to call /drivers/{id}
public record TruckResponse(
    Long id,
    String name,
    DriverSummaryDto driver  // Embedded denormalized data
) {}

public record DriverSummaryDto(
    Long id,
    String name,
    String licenseNumber
) {}
```

### 4. Nested Responses Only When Needed
```java
// List: minimal
public record TruckListResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    LocalDateTime createdAt
) {}

// Detail: full
public record TruckResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    String description,
    AddressDto address,
    DriverSummaryDto driver,
    List<AssignmentSummaryDto> activeAssignments,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

### 5. Consistent Naming Across Resources
```java
// All list responses follow same pattern
TruckListResponse:    id, name, type, status, createdAt
DriverListResponse:   id, name, status, licenseNumber, createdAt
AssignmentListResponse: id, truckName, driverName, status, startDate, createdAt

// All detail responses include audit
TruckResponse:        ... createdAt, updatedAt
DriverResponse:       ... createdAt, updatedAt
```

## Pagination Conventions

### Request Parameters
| Param | Default | Max | Description |
|-------|---------|-----|-------------|
| `page` | 0 | - | Zero-based page index |
| `size` | 20 | 100 | Elements per page |
| `sort` | `createdAt,desc` | - | `field,direction` (comma-separated) |

```java
// Controller helper
private Pageable toPageable(int page, int size, String sort) {
    String[] parts = sort.split(",");
    String field = parts[0];
    Sort.Direction direction = parts.length > 1 
        ? Sort.Direction.fromString(parts[1]) 
        : Sort.Direction.DESC;
    
    // Validate field against allowed sort fields
    if (!ALLOWED_SORT_FIELDS.contains(field)) {
        throw new ValidationException("Invalid sort field: " + field);
    }
    
    return PageRequest.of(page, Math.min(size, 100), Sort.by(direction, field));
}
```

### Response Metadata
```json
{
  "content": [...],
  "metadata": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "first": true,
    "last": false,
    "sort": {
      "fields": [
        {"property": "createdAt", "direction": "desc"}
      ]
    }
  }
}
```

## Filtering

### Simple Filters (Query Params)
```java
@GetMapping("/trucks")
public PageResponse<TruckListResponse> list(
        @RequestParam(required = false) TruckType type,
        @RequestParam(required = false) TruckStatus status,
        @RequestParam(required = false) String nameContains,
        Pageable pageable) { ... }
```

### Complex Filters (Filter DTO)
```java
@GetMapping("/trucks")
public PageResponse<TruckListResponse> list(
        @ModelAttribute TruckFilterRequest filter,
        Pageable pageable) { ... }

public record TruckFilterRequest(
    TruckType type,
    TruckStatus status,
    String nameContains,
    LocalDate createdAfter,
    LocalDate createdBefore,
    Long driverId,
    Boolean hasDriver
) {}
```

## Field Selection (Optional)

For large responses, allow clients to request specific fields:
```java
@GetMapping("/trucks/{id}")
public ResponseEntity<TruckResponse> get(
        @PathVariable Long id,
        @RequestParam(required = false) String fields) {  // e.g., "id,name,status"
    // Implementation: use GraphQL or dynamic serialization
}
```

## Versioning Strategy

### URL Versioning (Preferred for Breaking Changes)
```
/api/v1/trucks
/api/v2/trucks
```

### Header Versioning (Alternative)
```
Accept: application/vnd.example.v1+json
```

### Field Deprecation (Non-Breaking)
```java
@Deprecated(forRemoval = true, since = "2.0")
@JsonProperty("oldFieldName")
public String getOldFieldName() { return newFieldName; }
```

## Compression & Performance

```yaml
# application.yml
server:
  compression:
    enabled: true
    mime-types: application/json
    min-response-size: 1024
```

## Testing Response Contracts

```java
@WebMvcTest(TruckController.class)
class TruckControllerResponseTest {

    @Autowired MockMvc mockMvc;
    @MockBean TruckService truckService;

    @Test
    void listResponseShouldHaveCorrectStructure() throws Exception {
        TruckListResponse item = new TruckListResponse(1L, "Truck 1", TruckType.FLATBED, 
            TruckStatus.ACTIVE, LocalDateTime.now());
        
        when(truckService.list(any(), any())).thenReturn(
            new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/trucks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Truck 1"))
            .andExpect(jsonPath("$.metadata.totalElements").value(1))
            .andExpect(jsonPath("$.metadata.page").value(0));
    }

    @Test
    void detailResponseShouldNotContainInternalFields() throws Exception {
        TruckResponse response = new TruckResponse(1L, "Truck", ..., null, null, null);
        when(truckService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/trucks/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId").doesNotExist())
            .andExpect(jsonPath("$.version").doesNotExist())
            .andExpect(jsonPath("$.deleted").doesNotExist());
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| Returning `Page<T>` directly | Exposes Spring internals | Wrap in `PageResponse<T>` |
| Different response shapes per endpoint | Client confusion | Standardize: list = `PageResponse`, detail = direct object |
| Exposing entity fields | Leaks internals | Explicit DTOs only |
| No pagination on collections | OOM risk, slow | Always paginate |
| Inconsistent error format | Client can't handle uniformly | Global `@RestControllerAdvice` |
| Deep nesting in responses | N+1, large payloads | Denormalize or separate endpoints |