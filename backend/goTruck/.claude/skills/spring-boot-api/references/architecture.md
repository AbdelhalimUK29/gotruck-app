# API Architecture: Controller → Service → Repository

## Layer Responsibilities

### Controller (Presentation Layer)
**Primary concerns:**
- HTTP protocol handling (request/response, status codes, headers)
- Request validation (`@Valid`, method-level validation)
- Authentication/authorization context extraction (`@AuthenticationPrincipal`, `SecurityContext`)
- Mapping HTTP request data → application layer (DTOs)
- Returning appropriate HTTP responses

**Must NOT contain:**
- Business logic
- Database queries
- Transaction management
- Complex data transformations

```java
@RestController
@RequestMapping("/api/trucks")
@RequiredArgsConstructor
public class TruckController {
    private final TruckService truckService;

    @GetMapping("/{id}")
    public ResponseEntity<TruckResponse> getTruck(@PathVariable Long id) {
        return ResponseEntity.ok(truckService.getTruckById(id));
    }

    @PostMapping
    public ResponseEntity<TruckResponse> createTruck(@Valid @RequestBody CreateTruckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(truckService.createTruck(request));
    }
}
```

### Service (Application/Business Layer)
**Primary concerns:**
- Business rules and domain logic
- Orchestrating multiple repositories
- Transaction boundaries (`@Transactional`)
- Authorization checks (resource ownership, permissions)
- Coordination between domain objects
- Mapping between DTOs and entities (delegated to MapStruct)

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TruckService {
    private final TruckRepository truckRepository;
    private final TruckMapper truckMapper;
    private final TenantContext tenantContext;

    public TruckResponse getTruckById(Long id) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + id));
        return truckMapper.toResponse(truck);
    }

    @Transactional
    public TruckResponse createTruck(CreateTruckRequest request) {
        Truck truck = truckMapper.toEntity(request);
        truck.setTenantId(tenantContext.getTenantId());
        Truck saved = truckRepository.save(truck);
        return truckMapper.toResponse(saved);
    }
}
```

### Repository (Persistence Layer)
**Primary concerns:**
- Database access and queries
- Entity persistence (save, delete)
- Custom query methods for specific data requirements
- Projections and DTO queries for performance

**Must NOT contain:**
- Business logic (unless persistence-specific, e.g., optimistic locking)
- HTTP concerns
- Authorization logic

```java
public interface TruckRepository extends JpaRepository<Truck, Long> {
    // Efficient query for API needs
    @Query("SELECT new com.example.dto.TruckListDto(t.id, t.name, t.status) " +
           "FROM Truck t WHERE t.tenantId = :tenantId AND t.status = :status")
    Page<TruckListDto> findActiveTrucksByTenant(Long tenantId, TruckStatus status, Pageable pageable);

    // Existence check without loading entity
    boolean existsByIdAndTenantId(Long id, Long tenantId);
}
```

## Anti-Patterns to Avoid

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| Business logic in controller | Untestable, couples HTTP to domain | Move to service |
| Repository returning entities for read-only APIs | Over-fetching, N+1 risk | Use projections/DTOs |
| Service calling multiple repos in loop | N+1, no transaction boundary | Single optimized query |
| Controller managing transactions | Wrong layer | `@Transactional` on service |
| Giant "Manager" service | Low cohesion, hard to maintain | Split by domain capability |

## Package Structure Convention

```
com.example
├── controller/          # REST controllers only
├── service/             # Business logic
│   ├── TruckService.java
│   └── impl/            # If multiple implementations
├── repository/          # Spring Data repositories
├── dto/                 # API contracts
│   ├── request/
│   └── response/
├── mapper/              # MapStruct mappers
├── entity/              # JPA entities
├── exception/           # Custom exceptions
├── security/            # AuthN/AuthZ config
└── config/              # Configuration classes
```