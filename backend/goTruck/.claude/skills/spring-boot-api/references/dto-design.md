# DTO-First API Design

## Core Principle

**Never expose JPA entities directly through REST APIs.**

Entities are persistence models — they contain database-specific details, bidirectional relationships, audit fields, and internal state that must not leak to API consumers.

## DTO Categories

### Request DTOs (Input)
```java
// Creation - includes only fields the client provides
public record CreateTruckRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull TruckType type,
    @Size(max = 500) String description,
    @Valid AddressDto address
) {}

// Update - often partial; use @NotNull only for required fields
public record UpdateTruckRequest(
    @Size(max = 100) String name,
    TruckType type,
    @Size(max = 500) String description,
    @Valid AddressDto address,
    TruckStatus status
) {}

// Query/filter DTOs for complex search
public record TruckFilterRequest(
    TruckType type,
    TruckStatus status,
    String nameContains,
    LocalDate createdAfter,
    LocalDate createdBefore
) {}
```

### Response DTOs (Output)
```java
// List view - minimal fields for collections
public record TruckListResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    LocalDateTime createdAt
) {}

// Detail view - full representation for single resource
public record TruckResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    String description,
    AddressDto address,
    Long driverId,
    String driverName,  // denormalized for convenience
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// Nested representation when needed
public record TruckWithAssignmentsResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    List<AssignmentSummaryDto> activeAssignments
) {}
```

## DTO Design Rules

### 1. Dedicated DTOs per Use Case
| Use Case | DTO Pattern |
|----------|-------------|
| Create | `CreateXRequest` |
| Update (full) | `UpdateXRequest` |
| Update (partial/PATCH) | `PatchXRequest` or `UpdateXRequest` with optional fields |
| List | `XListResponse` |
| Detail | `XResponse` |
| Nested/Related | `XWithYResponse` |

### 2. Only Expose What's Needed
```java
// ENTITY (internal)
@Entity
public class Truck {
    @Id Long id;
    String name;
    TruckType type;
    TruckStatus status;
    String description;
    @Embedded Address address;
    Long driverId;
    Long tenantId;           // NEVER expose
    String createdBy;        // NEVER expose
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    @Version Long version;   // NEVER expose (optimistic locking)
    boolean deleted;         // NEVER expose (soft delete)
}

// RESPONSE DTO (external)
public record TruckResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    String description,
    AddressDto address,
    Long driverId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

### 3. Exclude Sensitive/Internal Fields
| Field Category | Example | Expose? |
|----------------|---------|---------|
| Primary keys (when not needed) | `id` on nested objects | ❌ |
| Foreign keys (internal refs) | `tenantId`, `createdBy` | ❌ |
| Audit fields | `createdAt`, `updatedAt`, `version` | ✅ sometimes |
| Soft delete flags | `deleted`, `deletedAt` | ❌ |
| Passwords/secrets | `passwordHash`, `apiKey` | ❌ |
| Permission flags | `isAdmin`, `roles` | ❌ |
| Database internals | `@Version`, `@Transient` | ❌ |

### 4. Use Records for Immutable DTOs (Java 17+)
```java
public record CreateTruckRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull TruckType type
) {
    // Compact constructor for additional validation
    public CreateTruckRequest {
        if (name != null && name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
    }
}
```

### 5. Nested DTOs for Related Data
```java
public record AddressDto(
    @NotBlank @Size(max = 200) String street,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @Size(max = 100) String country
) {}

// In response - denormalize when it avoids extra calls
public record TruckResponse(
    Long id,
    String name,
    AddressDto address  // embedded, not a separate API call
) {}
```

## Validation on DTOs

```java
public record CreateTruckRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    @NotNull(message = "Type is required")
    TruckType type,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    @Valid  // cascades validation to nested DTO
    AddressDto address
) {}
```

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| Single `TruckDto` for all operations | Over-fetching on create, missing fields on update | Dedicated DTOs per operation |
| Exposing entity directly | Leaks internal structure, coupling | Always map to DTO |
| Optional fields on create DTO | Confusing API contract | Only required fields on create |
| No validation on request DTOs | Garbage data enters system | Jakarta Bean Validation on all inputs |
| Returning `List<Entity>` | Serialization issues, over-fetching | Return `List<ResponseDto>` |