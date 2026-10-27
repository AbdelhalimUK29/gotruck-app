# Repository Design Patterns

## Core Principle

**Repositories expose meaningful persistence operations, not generic CRUD.** Each method should correspond to a specific API/data requirement.

## Repository Interface Structure

```java
public interface TruckRepository extends JpaRepository<Truck, Long> {

    // ===== EXISTENCE CHECKS (lightweight) =====
    boolean existsByIdAndTenantId(Long id, Long tenantId);
    boolean existsByLicensePlateAndTenantId(String licensePlate, Long tenantId);

    // ===== PROJECTIONS FOR READ APIs (preferred) =====
    @Query("SELECT new com.example.dto.TruckListResponse(" +
           "t.id, t.name, t.type, t.status, t.createdAt) " +
           "FROM Truck t WHERE t.tenantId = :tenantId")
    Page<TruckListResponse> findListResponsesByTenant(Long tenantId, Pageable pageable);

    @Query("SELECT new com.example.dto.TruckListResponse(" +
           "t.id, t.name, t.type, t.status, t.createdAt, d.name) " +
           "FROM Truck t LEFT JOIN t.driver d WHERE t.tenantId = :tenantId")
    Page<TruckListResponse> findListResponsesWithDriverByTenant(Long tenantId, Pageable pageable);

    @Query("SELECT new com.example.dto.TruckResponse(" +
           "t.id, t.name, t.type, t.status, t.description, " +
           "t.addressStreet, t.addressCity, t.addressPostalCode, t.addressCountry, " +
           "t.driver.id, t.driver.name) " +
           "FROM Truck t LEFT JOIN t.driver d WHERE t.id = :id AND t.tenantId = :tenantId")
    Optional<TruckResponse> findResponseByIdAndTenantId(Long id, Long tenantId);

    // ===== ENTITY LOADING FOR WRITE OPERATIONS =====
    @EntityGraph(attributePaths = {"driver"})
    Optional<Truck> findWithDriverByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"driver", "assignments"})
    Optional<Truck> findWithDetailsByIdAndTenantId(Long id, Long tenantId);

    // ===== SPECIFIC BUSINESS QUERIES =====
    List<Truck> findByTenantIdAndStatusAndType(Long tenantId, TruckStatus status, TruckType type);
    
    long countByTenantIdAndStatus(Long tenantId, TruckStatus status);
    
    @Query("SELECT t FROM Truck t WHERE t.tenantId = :tenantId AND t.status = :status " +
           "AND t.driver IS NULL")
    List<Truck> findUnassignedByTenantAndStatus(Long tenantId, TruckStatus status);

    // ===== BATCH OPERATIONS =====
    @Modifying
    @Query("UPDATE Truck t SET t.status = :status WHERE t.id IN :ids AND t.tenantId = :tenantId")
    int updateStatusByIdsAndTenantId(@Param("ids") List<Long> ids, 
                                      @Param("status") TruckStatus status,
                                      @Param("tenantId") Long tenantId);
}
```

## Method Categories

### 1. Existence Checks (Lightweight)
```java
// Use for validation - no entity loading
boolean existsByIdAndTenantId(Long id, Long tenantId);
boolean existsByLicensePlateAndTenantId(String licensePlate, Long tenantId);
```

### 2. DTO Projections (Read APIs)
```java
// Returns DTO directly - no entity hydration, no mapping, no N+1
Page<TruckListResponse> findListResponsesByTenant(Long tenantId, Pageable pageable);
Optional<TruckResponse> findResponseByIdAndTenantId(Long id, Long tenantId);
```

### 3. EntityGraph for Write Operations
```java
// Loads entity with required relationships for modification
@EntityGraph(attributePaths = {"driver", "assignments"})
Optional<Truck> findWithDetailsByIdAndTenantId(Long id, Long tenantId);
```

### 4. Derived Query Methods (Simple)
```java
// Spring Data generates query from method name
List<Truck> findByTenantIdAndStatus(Long tenantId, TruckStatus status);
List<Truck> findByTenantIdAndType(Long tenantId, TruckType type);
```

### 5. Custom JPQL for Complex Queries
```java
@Query("SELECT t FROM Truck t WHERE t.tenantId = :tenantId " +
       "AND t.status = :status " +
       "AND t.driver IS NULL")
List<Truck> findUnassignedByTenantAndStatus(Long tenantId, TruckStatus status);
```

## Projection Types

### Interface Projection (Closed)
```java
public interface TruckSummary {
    Long getId();
    String getName();
    TruckStatus getStatus();
}

// Repository
List<TruckSummary> findByTenantId(Long tenantId);
```

### DTO Projection (Open - Constructor)
```java
public record TruckListResponse(
    Long id, String name, TruckType type, TruckStatus status, LocalDateTime createdAt
) {}

// Repository - fully qualified class name required
@Query("SELECT new com.example.dto.TruckListResponse(t.id, t.name, t.type, t.status, t.createdAt) " +
       "FROM Truck t WHERE t.tenantId = :tenantId")
Page<TruckListResponse> findListResponsesByTenant(Long tenantId, Pageable pageable);
```

## Query Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Tenant isolation** | Every query includes `tenantId` filter |
| **Only required data** | Projections for reads, EntityGraph for writes |
| **Single query per endpoint** | Avoid multiple repository calls |
| **Pagination at DB level** | `Page<T>` return type, `Pageable` parameter |
| **Indexes match queries** | Composite indexes for multi-column WHERE |

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| `findAll()` for tenant data | Loads all tenants' data | `findByTenantId(...)` |
| `findById()` then check tenant | Two queries, race condition | `findByIdAndTenantId()` |
| Multiple repo calls in service | N+1, no transaction boundary | Single optimized query |
| Returning `List<Entity>` for API | Over-fetching, serialization issues | Return `List<DTO>` |
| Generic `save()`/`delete()` in service | No business context | Wrap with meaningful method names |
| `@Query` without tenant filter | Security vulnerability | Always include tenantId |

## Naming Conventions

| Pattern | Example |
|---------|---------|
| Existence | `existsBy[Field]AndTenantId` |
| Single entity (write) | `findWith[Relations]ByIdAndTenantId` |
| Projection (read) | `find[DtoName]By[Criteria]` |
| Count | `countBy[Criteria]AndTenantId` |
| Batch update | `update[Field]By[Criteria]AndTenantId` |
| Bulk delete | `deleteBy[Criteria]AndTenantId` |

## Testing Repositories

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TruckRepositoryTest {

    @Autowired TruckRepository truckRepository;
    @Autowired TestEntityManager entityManager;

    @Test
    void shouldFindListResponsesByTenant() {
        // Given
        Tenant tenant = persistTenant();
        persistTrucks(tenant.getId(), 5);
        Long otherTenant = persistTenant();
        persistTrucks(otherTenant, 3);

        // When
        Page<TruckListResponse> page = truckRepository.findListResponsesByTenant(
            tenant.getId(), PageRequest.of(0, 10));

        // Then
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void shouldNotFindOtherTenantData() {
        Long tenantA = persistTenant();
        persistTrucks(tenantA, 2);
        Long tenantB = persistTenant();
        persistTrucks(tenantB, 3);

        Page<TruckListResponse> page = truckRepository.findListResponsesByTenant(
            tenantA, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldProjectOnlyRequiredFields() {
        Long tenantId = persistTenant();
        Truck truck = persistTruck(tenantId);

        // Verify only 1 query, no driver/assignments loaded
        TruckListResponse response = truckRepository.findListResponsesByTenant(
            tenantId, PageRequest.of(0, 10)).getContent().getFirst();

        assertThat(response.id()).isEqualTo(truck.getId());
        assertThat(response.name()).isEqualTo(truck.getName());
        // No driver/address fields in list response
    }
}
```

## Repository vs Service Responsibility

| Repository | Service |
|------------|---------|
| Data access only | Business logic |
| Returns data (entities/DTOs) | Orchestrates, validates, authorizes |
| No authorization | Authorizes every operation |
| No transaction management (mostly) | Manages transactions |
| Single table/entity focus | Cross-entity operations |