# JPA Entity Design Guidelines

## Core Principle

**JPA entities are persistence models, NOT API contracts.** They contain database-specific concerns that must not leak to clients.

## Entity Structure

### Base Entity (Common Fields)
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
}
```

### Concrete Entity
```java
@Entity
@Table(name = "trucks", 
    indexes = {
        @Index(name = "idx_truck_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_truck_tenant_type", columnList = "tenant_id, type"),
        @Index(name = "idx_truck_license_plate", columnList = "license_plate", unique = true)
    })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "long"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Truck extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TruckType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TruckStatus status = TruckStatus.ACTIVE;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "license_plate", length = 20, unique = true)
    private String licensePlate;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "address_street", length = 200)),
        @AttributeOverride(name = "city", column = @Column(name = "address_city", length = 100)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "address_postal_code", length = 20)),
        @AttributeOverride(name = "country", column = @Column(name = "address_country", length = 100))
    })
    private Address address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", foreignKey = @ForeignKey(name = "fk_truck_driver"))
    private Driver driver;

    @OneToMany(mappedBy = "truck", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Assignment> assignments = new HashSet<>();

    @OneToMany(mappedBy = "truck", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<MaintenanceRecord> maintenanceRecords = new HashSet<>();
}
```

## Relationship Guidelines

### FetchType: Default to LAZY
```java
// ✅ DEFAULT - LAZY for all relationships
@ManyToOne(fetch = FetchType.LAZY)
@OneToMany(fetch = FetchType.LAZY)
@OneToOne(fetch = FetchType.LAZY)

// ❌ NEVER use EAGER as default - causes over-fetching
@ManyToOne(fetch = FetchType.EAGER)  // WRONG
```

> **Exception**: `@OneToOne` with `@MapsId` (shared primary key) can be EAGER since it's a 1:1 mandatory relationship.

### Bidirectional Relationships

```java
// Parent (owning side)
@Entity
public class Truck {
    @OneToMany(mappedBy = "truck", fetch = FetchType.LAZY, 
               cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<MaintenanceRecord> maintenanceRecords = new HashSet<>();
}

// Child (non-owning side)
@Entity
public class MaintenanceRecord {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;
}
```

**Rules for bidirectional:**
- One side owns the relationship (has the FK column) → no `mappedBy`
- Other side uses `mappedBy` referencing the field name on owning side
- Cascade/orphanRemoval on owning side only
- Helper methods to maintain both sides:
```java
public void addMaintenanceRecord(MaintenanceRecord record) {
    maintenanceRecords.add(record);
    record.setTruck(this);
}

public void removeMaintenanceRecord(MaintenanceRecord record) {
    maintenanceRecords.remove(record);
    record.setTruck(null);
}
```

### Cascade & OrphanRemoval

| Scenario | Cascade | OrphanRemoval |
|----------|---------|---------------|
| Child cannot exist without parent | `CascadeType.ALL` | `true` |
| Child has independent lifecycle | None / `PERSIST`, `MERGE` | `false` |
| Parent deletion should delete children | `REMOVE` / `ALL` | `true` |

```java
// MaintenanceRecord CANNOT exist without Truck
@OneToMany(mappedBy = "truck", fetch = FetchType.LAZY, 
           cascade = CascadeType.ALL, orphanRemoval = true)
private Set<MaintenanceRecord> maintenanceRecords = new HashSet<>();

// Assignment exists independently (may be reassigned)
@OneToMany(mappedBy = "truck", fetch = FetchType.LAZY)
private Set<Assignment> assignments = new HashSet<>();
```

## Equals & HashCode

**Use business key, NOT ID** (ID is null before persist).

```java
@Entity
@Getter @Setter
public class Truck extends BaseEntity {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Truck truck)) return false;
        // Business key: licensePlate + tenantId (unique per tenant)
        return Objects.equals(licensePlate, truck.licensePlate) 
            && Objects.equals(tenantId, truck.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(licensePlate, tenantId);
    }
}
```

**Why not ID?**
- New entities have `id = null` → all new entities equal!
- Business key is stable across persist/merge

## JSON Serialization

**Never serialize entities directly.** Use DTOs instead.

If you must serialize (e.g., for Kafka events), prevent cycles:
```java
@Entity
public class Truck {
    @JsonIgnoreProperties("truck")  // Breaks cycle on driver.trucks
    @ManyToOne(fetch = FetchType.LAZY)
    private Driver driver;

    @JsonIgnore  // Never serialize collections in entity
    @OneToMany(mappedBy = "truck")
    private Set<Assignment> assignments;
}
```

## Entity Best Practices

| Practice | Why |
|----------|-----|
| `fetch = FetchType.LAZY` default | Prevents over-fetching |
| Explicit `@JoinColumn` | Clear FK naming, avoids defaults |
| `@Builder` with `@Builder.Default` for collections | Clean instantiation |
| `@FilterDef` + `@Filter` for tenant | Automatic tenant isolation |
| Composite indexes for multi-col queries | Query performance |
| Unique constraints in DB | Data integrity at source |
| `@Embedded` for value objects | Clean mapping (Address) |
| Enum as STRING | Readable DB, schema evolution |
| No business logic in entities | Entities are data carriers |

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| EAGER fetching globally | Loads entire graph always | Use LAZY + EntityGraph/projections |
| `cascade = ALL` on independent entities | Accidental deletes | Only on true parent-child |
| `equals`/`hashCode` using ID | Broken in sets before persist | Use business key |
| Bidirectional without helper methods | Inconsistent object graph | Add `addX`/`removeX` methods |
| Exposing entities in REST | Leaks internals, serialization issues | Always DTO |
| `@JsonIgnore` on everything | Hides symptoms, not cause | Don't serialize entities |
| Logic in getters/setters | Unexpected side effects | Keep entities dumb |