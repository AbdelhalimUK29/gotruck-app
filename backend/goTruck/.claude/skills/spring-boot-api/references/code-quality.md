# Code Quality Standards

## Core Principle

**Readable, maintainable code over clever code.** Optimize for the next developer who reads it.

## Naming Conventions

### Packages
```
com.example
├── controller/           # REST endpoints
├── service/              # Business logic
│   ├── impl/             # If multiple implementations
│   └── TruckService.java
├── repository/           # Data access
├── dto/                  # API contracts
│   ├── request/
│   └── response/
├── mapper/               # MapStruct mappers
├── entity/               # JPA entities
├── exception/            # Custom exceptions
├── security/             # AuthN/AuthZ
├── config/               # Configuration
└── event/                # Domain events
```

### Classes
| Type | Convention | Example |
|------|------------|---------|
| Controller | `*Controller` | `TruckController` |
| Service | `*Service` | `TruckService` |
| Repository | `*Repository` | `TruckRepository` |
| Entity | Domain noun | `Truck`, `Driver` |
| DTO (request) | `*Request` | `CreateTruckRequest` |
| DTO (response) | `*Response` | `TruckResponse` |
| DTO (list) | `*ListResponse` | `TruckListResponse` |
| Mapper | `*Mapper` | `TruckMapper` |
| Exception | `*Exception` | `ResourceNotFoundException` |
| Event | `*Event` | `TruckCreatedEvent` |
| Config | `*Config` | `SecurityConfig` |

### Methods
| Type | Convention | Example |
|------|------------|---------|
| Query (read) | `find*`, `get*`, `list*`, `count*` | `findById`, `listByTenant` |
| Command (write) | `create*`, `update*`, `delete*`, `assign*` | `createTruck`, `assignDriver` |
| Boolean | `exists*`, `has*`, `can*`, `is*` | `existsByLicensePlate` |
| Event handlers | `on*`, `handle*` | `onTruckCreated` |

### Variables
| Scope | Convention |
|-------|------------|
| Local/Parameter | `camelCase` |
| Constants | `UPPER_SNAKE_CASE` |
| Boolean | `is*`, `has*`, `should*`, `can*` |
| Collections | Plural noun: `trucks`, `driverIds` |

## Method Design

### Size Limits
- **Max 50 lines** per method (prefer < 20)
- **Max 4 parameters** (use parameter object/DTO)
- **Max 4 nesting levels** (use early returns)

### Single Responsibility
```java
// ❌ TOO MANY RESPONSIBILITIES
public TruckResponse create(CreateTruckRequest request) {
    validate(request);           // validation
    checkLicensePlateUnique();   // business rule
    mapToEntity(request);        // mapping
    enrichWithTenantAndUser();   // enrichment
    save(entity);                // persistence
    publishEvent();              // side effect
    return mapToResponse();      // mapping
}

// ✅ CLEAR DELEGATION
@Transactional
public TruckResponse create(CreateTruckRequest request) {
    validateCreateRequest(request);      // business validation
    Truck truck = truckMapper.toEntity(request);  // mapping
    enrichForCreation(truck);            // enrichment
    Truck saved = truckRepository.save(truck);    // persistence
    eventPublisher.publish(new TruckCreatedEvent(saved.getId()));  // side effect
    return truckMapper.toResponse(saved);           // mapping
}
```

### Early Returns Over Nesting
```java
// ❌ DEEP NESTING
public void assignDriver(Long truckId, Long driverId) {
    Truck truck = repo.find(truckId);
    if (truck != null) {
        if (truck.getDriver() == null) {
            Driver driver = driverRepo.find(driverId);
            if (driver != null) {
                if (driver.isAvailable()) {
                    truck.setDriver(driver);
                } else {
                    throw new DriverNotAvailableException();
                }
            } else {
                throw new DriverNotFoundException();
            }
        } else {
            throw new TruckAlreadyAssignedException();
        }
    } else {
        throw new TruckNotFoundException();
    }
}

// ✅ EARLY RETURNS
public void assignDriver(Long truckId, Long driverId) {
    Truck truck = truckRepository.findByIdAndTenantId(truckId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Truck", truckId));

    if (truck.getDriver() != null) {
        throw new BusinessRuleViolationException("TRUCK_ALREADY_ASSIGNED", "...");
    }

    Driver driver = driverRepository.findByIdAndTenantId(driverId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));

    if (driver.getStatus() != DriverStatus.AVAILABLE) {
        throw new BusinessRuleViolationException("DRIVER_NOT_AVAILABLE", "...");
    }

    truck.setDriver(driver);
    driver.setStatus(DriverStatus.ASSIGNED);
}
```

## Constructor Injection
```java
// ✅ REQUIRED - Immutable dependencies, testable
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class TruckService {
    private final TruckRepository truckRepository;
    private final DriverRepository driverRepository;
    private final TruckMapper truckMapper;
    private final AuthorizationService authService;
}

// ❌ FIELD INJECTION - Hard to test, mutable
@Service
public class TruckService {
    @Autowired private TruckRepository truckRepository;
    @Autowired private DriverRepository driverRepository;
}
```

## Immutability

### DTOs as Records (Java 17+)
```java
public record CreateTruckRequest(
    @NotBlank String name,
    @NotNull TruckType type,
    String description
) {}

public record TruckResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status
) {}
```

### Entities: Mutable but Controlled
```java
@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA requires
public class Truck extends BaseEntity {
    // Business methods mutate state intentionally
    public void assignDriver(Driver driver) {
        this.driver = driver;
    }
    
    public void releaseDriver() {
        this.driver = null;
    }
}
```

## Constants Over Magic Values

```java
// ❌ MAGIC NUMBERS/STRINGS
if (truck.getCapacity() > 50000) { ... }
if ("FLATBED".equals(type)) { ... }
pageable = PageRequest.of(0, 20);

// ✅ NAMED CONSTANTS
public class TruckConstants {
    public static final int MAX_CAPACITY_KG = 50000;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}

if (truck.getCapacity() > TruckConstants.MAX_CAPACITY_KG) { ... }
pageable = PageRequest.of(0, TruckConstants.DEFAULT_PAGE_SIZE);
```

## Exception Handling

### Custom Exceptions with Codes
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
}
```

### Throw Early, Catch Late
```java
// Service throws, controller doesn't catch
public TruckResponse getById(Long id) {
    return truckRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Truck", id));
}

// Global handler catches
@RestControllerAdvice
public class GlobalExceptionHandler { ... }
```

## Documentation

### JavaDoc for Public APIs Only
```java
/**
 * Assigns a driver to a truck.
 * 
 * @param truckId the truck to assign
 * @param driverId the driver to assign
 * @return updated truck response
 * @throws ResourceNotFoundException if truck or driver not found in tenant
 * @throws BusinessRuleViolationException if truck already has driver or driver unavailable
 */
public TruckResponse assignDriver(Long truckId, Long driverId) { ... }
```

### No Redundant Comments
```java
// ❌ REDUNDANT
// Sets the name of the truck
truck.setName(name);

// ✅ EXPLAINS WHY (non-obvious)
truck.setStatus(TruckStatus.MAINTENANCE);  // Triggers maintenance workflow
```

## Formatting & Tools

### Enforced via Build
```xml
<!-- Maven: spotless-maven-plugin -->
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.23.0</version>
                <style>GOOGLE</style>
            </googleJavaFormat>
            <removeUnusedImports/>
            <trimTrailingWhitespace/>
        </java>
    </configuration>
</plugin>
```

```gradle
// Gradle: spotless
spotless {
    java {
        googleJavaFormat '1.23.0'
        removeUnusedImports()
        trimTrailingWhitespace()
    }
}
```

### Imports Order
1. Static imports
2. Java/standard library
3. Third-party
4. Project packages
5. Blank line between groups

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| Giant controller/service | Hard to understand, test | Split by aggregate |
| `Optional` as field/parameter | Not serializable, unclear nullability | Use null or explicit checks |
| `Optional` in streams withoutterminal | Lazy eval confusion | Collect to List first |
| `stream().filter().findFirst().orElseThrow()` in loops | N+1 | Batch query |
| `Map<String, Object>` for structured data | Type unsafe, no documentation | Use DTO/record |
| `@Data` on entities | Generates `equals`/`hashCode` incorrectly | Use `@Getter @Setter` only |
| Public setters on DTOs | Mutation after creation | Use records |
| Catching `Exception` broadly | Hides bugs | Catch specific exceptions |
| `System.out.println` in code | Not production logging | Use SLF4J |