# Database Query Optimization

## Core Principle

**Queries should retrieve ONLY the data required by the API.** Every extra column, row, or join costs CPU, I/O, and network.

## Optimization Strategies

### 1. Avoid SELECT * — Use Projections

```java
// ❌ BAD - Loads entire entity (all columns, all relationships potentially)
List<Truck> trucks = truckRepository.findAll();

// ✅ GOOD - DTO projection, only required columns
@Query("SELECT new com.example.dto.TruckListResponse(t.id, t.name, t.status) FROM Truck t")
List<TruckListResponse> findAllListResponses();

// ✅ GOOD - Interface projection (Spring Data)
public interface TruckSummary {
    Long getId();
    String getName();
    TruckStatus getStatus();
}
List<TruckSummary> findByTenantId(Long tenantId, Pageable pageable);
```

### 2. Database-Level Pagination

```java
// ❌ BAD - Loads all into memory, then pages
List<Truck> all = truckRepository.findAll();
return all.subList(offset, offset + size);

// ✅ GOOD - Database handles pagination
Page<TruckListResponse> findByTenantId(Long tenantId, Pageable pageable);

// Controller
@GetMapping
public PageResponse<TruckListResponse> list(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "createdAt,desc") String sort
) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(parseSort(sort)));
    return truckService.findAll(pageable);
}
```

### 3. Filter at Database Level

```java
// ❌ BAD - Load all, filter in Java
List<Truck> trucks = truckRepository.findAll();
return trucks.stream()
    .filter(t -> t.getStatus() == TruckStatus.ACTIVE)
    .filter(t -> t.getType() == type)
    .toList();

// ✅ GOOD - Push filters to WHERE clause
@Query("SELECT t FROM Truck t WHERE t.tenantId = :tenantId AND t.status = :status AND t.type = :type")
Page<Truck> findByTenantIdAndStatusAndType(Long tenantId, TruckStatus status, TruckType type, Pageable pageable);
```

### 4. Avoid Queries in Loops

```java
// ❌ BAD - N+1 repository calls
for (Long truckId : truckIds) {
    Truck truck = truckRepository.findById(truckId).orElseThrow();
    process(truck);
}

// ✅ GOOD - Single IN query
List<Truck> trucks = truckRepository.findAllById(truckIds);
for (Truck truck : trucks) {
    process(truck);
}

// ✅ GOOD - Batch processing for large sets
truckRepository.findAllByIdInBatches(truckIds, batchSize)
    .forEach(this::process);
```

### 5. Eliminate Duplicate Queries

```java
// ❌ BAD - Same query twice
boolean exists = truckRepository.existsById(id);
Truck truck = truckRepository.findById(id).orElseThrow();  // Duplicate!

// ✅ GOOD - Single query
Truck truck = truckRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + id));

// ✅ GOOD - Check existence without loading (for validation only)
if (!truckRepository.existsByIdAndTenantId(id, tenantId)) {
    throw new ResourceNotFoundException("Truck not found: " + id);
}
// Then load if needed for update
```

### 6. Use EXISTS Instead of COUNT When Possible

```java
// ❌ BAD - Full count scan
long count = truckRepository.countByTenantIdAndStatus(tenantId, TruckStatus.ACTIVE);
if (count > 0) { ... }

// ✅ GOOD - Stops at first match
boolean hasActive = truckRepository.existsByTenantIdAndStatus(tenantId, TruckStatus.ACTIVE);
```

### 7. Optimize Join Strategies

```java
// For to-one relationships needed in projection
@Query("SELECT new dto.TruckResponse(t.id, t.name, d.name) " +
       "FROM Truck t LEFT JOIN t.driver d WHERE t.id = :id")
Optional<TruckResponse> findResponseById(Long id);

// For to-many: use separate query or fetch join carefully
// Avoid: JOIN FETCH on to-many with pagination (causes duplicates)
```

## Indexing Strategy

### When to Add Indexes

| Query Pattern | Index Needed |
|---------------|--------------|
| `WHERE tenant_id = ? AND status = ?` | Composite: `(tenant_id, status)` |
| `WHERE tenant_id = ? ORDER BY created_at DESC` | Composite: `(tenant_id, created_at DESC)` |
| `WHERE name ILIKE '%term%'` | GIN/trigram (PostgreSQL) or full-text |
| `JOIN driver ON truck.driver_id = driver.id` | FK index on `driver_id` (auto) |

### Index Checklist

```sql
-- Multi-tenant: ALWAYS include tenant_id as leading column
CREATE INDEX idx_truck_tenant_status ON truck(tenant_id, status);
CREATE INDEX idx_truck_tenant_type ON truck(tenant_id, type);
CREATE INDEX idx_truck_tenant_created ON truck(tenant_id, created_at DESC);

-- For sorting + filtering
CREATE INDEX idx_truck_tenant_status_created ON truck(tenant_id, status, created_at DESC);

-- Partial indexes for common filters
CREATE INDEX idx_truck_active ON truck(tenant_id, created_at DESC) 
WHERE status = 'ACTIVE' AND deleted = false;
```

> ⚠️ **Don't index blindly.** Each index slows writes. Add only when:
> - Query is frequent and slow (check `pg_stat_statements`)
> - EXPLAIN ANALYZE shows Seq Scan on large table
> - The index will actually be used (check query plan)

## Connection Pool & Query Tuning

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          fetch_size: 50          # Batch fetch size
          batch_size: 20          # Statement batching
        order_inserts: true
        order_updates: true
        generate_statistics: false  # Disable in prod
```

## Monitoring Slow Queries

```yaml
logging:
  level:
    org.hibernate.SQL: WARN      # Only slow queries
    org.hibernate.orm.jdbc.bind: WARN
```

Or use `p6spy` / `datasource-proxy` for production query logging with parameters.

## Checklist for Every New Query

- [ ] Uses projection (DTO/interface) instead of full entity?
- [ ] Pagination at database level?
- [ ] Filters pushed to WHERE clause?
- [ ] No queries in loops?
- [ ] No duplicate queries?
- [ ] Appropriate indexes exist (verify with EXPLAIN)?
- [ ] Tenant filter included for multi-tenant data?
- [ ] Fetch size configured for large result sets?