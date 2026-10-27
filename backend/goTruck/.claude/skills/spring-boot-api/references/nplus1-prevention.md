# N+1 Query Prevention

## The Problem

N+1 queries occur when an application executes **1 query to fetch parent entities + N queries to fetch related data for each parent**. This is a critical performance issue in JPA/Hibernate applications.

```java
// DANGEROUS - triggers N+1!
List<Truck> trucks = truckRepository.findAll();  // 1 query
for (Truck truck : trucks) {
    String driverName = truck.getDriver().getName();  // N queries! (LAZY loading)
}
```

## Detection Checklist

Before implementing ANY endpoint returning related data, answer:

| Question | Why It Matters |
|----------|----------------|
| Which relationships does the response need? | Determines join strategy |
| Which fields are actually required? | Enables projections |
| How many SQL queries will execute? | Target: 1-2 queries max |
| Does serialization access lazy relationships? | Jackson/MapStruct can trigger N+1 |
| Does MapStruct mapping access lazy relationships? | Generated code may trigger loads |

## Solutions (Ordered by Preference)

### 1. Repository Projections (BEST for Read APIs)
Returns DTOs directly — no entity loading, no mapping, no N+1.

```java
public interface TruckRepository extends JpaRepository<Truck, Long> {
    
    // DTO projection - single query, no entity hydration
    @Query("SELECT new com.example.dto.TruckListResponse(" +
           "t.id, t.name, t.type, t.status, t.createdAt) " +
           "FROM Truck t WHERE t.tenantId = :tenantId")
    Page<TruckListResponse> findListResponsesByTenant(Long tenantId, Pageable pageable);

    // With JOIN for denormalized field
    @Query("SELECT new com.example.dto.TruckListResponse(" +
           "t.id, t.name, t.type, t.status, t.createdAt, d.name) " +
           "FROM Truck t LEFT JOIN t.driver d WHERE t.tenantId = :tenantId")
    Page<TruckListResponse> findListResponsesWithDriverByTenant(Long tenantId, Pageable pageable);
}
```

### 2. EntityGraph (JPA Standard)
Explicitly declare which relationships to fetch eagerly for a specific query.

```java
@EntityGraph(attributePaths = {"driver", "assignments"})
@Query("SELECT t FROM Truck t WHERE t.tenantId = :tenantId")
Page<Truck> findWithDetailsByTenant(Long tenantId, Pageable pageable);

// Or on repository method
@EntityGraph(attributePaths = {"driver"})
Optional<Truck> findWithDriverById(Long id);
```

```java
// Usage in service
@EntityGraph(attributePaths = {"driver"})
public interface TruckRepository extends JpaRepository<Truck, Long> {
    Optional<Truck> findWithDriverById(Long id);
    
    @EntityGraph(attributePaths = {"driver", "maintenanceRecords"})
    List<Truck> findWithDetailsByTenantId(Long tenantId);
}
```

### 3. Fetch Joins in JPQL
Explicit JOIN FETCH in custom queries.

```java
@Query("SELECT DISTINCT t FROM Truck t " +
       "LEFT JOIN FETCH t.driver " +
       "LEFT JOIN FETCH t.assignments " +
       "WHERE t.tenantId = :tenantId")
List<Truck> findWithAllDetailsByTenant(Long tenantId);
```

> ⚠️ **Warning**: `DISTINCT` required to avoid duplicate parent entities in memory. Pagination with fetch joins is problematic — use EntityGraph or projections instead.

### 4. DTO Projections via Interface (Spring Data)
```java
public interface TruckSummary {
    Long getId();
    String getName();
    TruckType getType();
    TruckStatus getStatus();
    // No driver/address - only what's needed
}

public interface TruckRepository extends JpaRepository<Truck, Long> {
    // Spring Data generates optimized query automatically
    Page<TruckSummary> findByTenantId(Long tenantId, Pageable pageable);
}
```

### 5. Batch Fetching (Hibernate-Specific)
Globally configure batch size for a relationship.

```java
@Entity
public class Truck {
    @ManyToOne(fetch = FetchType.LAZY)
    @BatchSize(size = 20)  // Load up to 20 drivers in one query
    private Driver driver;
}
```

Or globally in `application.yml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 20
```

> Use as a safety net, not a primary solution. Projections/EntityGraph are more explicit and predictable.

## What NOT To Do

| Anti-Pattern | Why It's Wrong |
|--------------|----------------|
| Change `LAZY` to `EAGER` globally | Affects ALL queries, causes over-fetching, memory issues |
| `@Transactional` on controller to extend session | Leaks persistence concerns to web layer, connection pool exhaustion |
| Load all entities then filter in Java | Defeats database optimization, OOM risk |
| Use `@EntityGraph` on every method | Over-fetches when not needed; be selective |

## Decision Matrix

| Scenario | Recommended Approach |
|----------|---------------------|
| List endpoint, few fields, pagination | **Repository DTO Projection** |
| Detail endpoint, needs 1-2 relationships | **EntityGraph** or **Fetch Join** |
| Complex nested response, multiple levels | **DTO Projection with JOINs** |
| Relationship accessed occasionally | **Batch Fetching** (safety net) |
| Write operation (create/update) | Load entity normally (single ID) |

## Verification

Always verify query count:

1. **Enable SQL logging** temporarily:
   ```yaml
   logging:
     level:
       org.hibernate.SQL: DEBUG
       org.hibernate.orm.jdbc.bind: TRACE
   ```

2. **Check actual queries** in logs during endpoint test

3. **Use Spring Boot test** with `@AutoConfigureTestDatabase` to verify query count programmatically:
   ```java
   @Test
   void shouldNotCauseNPlus1() {
       // given: 10 trucks with drivers
       // when: call list endpoint
       // then: only 1-2 queries executed (not 11!)
   }
   ```

## Quick Reference: N+1 Triggers

| Code Pattern | Triggers N+1? |
|--------------|---------------|
| `entity.getLazyCollection().size()` | ✅ Yes |
| `entity.getLazySingle()` | ✅ Yes |
| MapStruct mapping accessing lazy field | ✅ Yes |
| Jackson serialization of entity with lazy rels | ✅ Yes |
| `repository.findAll()` then iterate | ✅ Yes |
| Projection DTO query | ❌ No |
| EntityGraph with required paths | ❌ No |
| Fetch join in JPQL | ❌ No |