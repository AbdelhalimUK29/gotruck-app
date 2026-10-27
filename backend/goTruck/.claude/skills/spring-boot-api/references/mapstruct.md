# MapStruct Usage for Entity ↔ DTO Mapping

## Core Principle

**Use MapStruct for ALL Entity ↔ DTO mapping. Never manually write repetitive mapping code.**

MapStruct generates type-safe, performant mapping code at compile time. Manual mapping is error-prone, verbose, and harder to maintain.

## Basic Mapper Configuration

### 1. Mapper Interface
```java
@Mapper(componentModel = "spring", 
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TruckMapper {

    // Entity → Response DTO
    TruckResponse toResponse(Truck truck);

    // Request DTO → Entity (for creation)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Truck toEntity(CreateTruckRequest request);

    // Request DTO → Existing Entity (for updates)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Truck truck, UpdateTruckRequest request);

    // List mapping
    List<TruckListResponse> toListResponse(List<Truck> trucks);
}
```

### 2. Key Configuration Options

| Option | Purpose | Recommended Value |
|--------|---------|-------------------|
| `componentModel` | Spring bean integration | `"spring"` |
| `nullValuePropertyMappingStrategy` | How to handle null source properties | `IGNORE` (for updates) |
| `unmappedTargetPolicy` | Fail if target property unmapped | `ERROR` (catches typos) |
| `uses` | Reuse other mappers | `AddressMapper.class` |

### 3. Nested Object Mapping
```java
@Mapper(componentModel = "spring", uses = AddressMapper.class)
public interface TruckMapper {

    TruckResponse toResponse(Truck truck);

    @Mapping(target = "address", source = "address")
    TruckResponse toResponse(Truck truck);  // Uses AddressMapper automatically
}
```

## Anti-Patterns & Solutions

### ❌ Anti-Pattern: Triggering Lazy Loading in Mapper
```java
// DANGEROUS - accesses lazy relationship during mapping!
@Mapper(componentModel = "spring")
public interface TruckMapper {
    TruckResponse toResponse(Truck truck);  // If TruckResponse has driverName, 
                                            // and truck.getDriver() is LAZY → N+1!
}
```

### ✅ Solution 1: Use Projections in Repository (Preferred)
```java
// Repository returns DTO directly - no entity loading, no mapping needed
public interface TruckRepository extends JpaRepository<Truck, Long> {
    @Query("SELECT new com.example.dto.TruckResponse(" +
           "t.id, t.name, t.type, t.status, d.name as driverName) " +
           "FROM Truck t LEFT JOIN t.driver d WHERE t.id = :id")
    Optional<TruckResponse> findResponseById(Long id);
}
```

### ✅ Solution 2: EntityGraph for Required Relationships
```java
// Service layer - fetch with required relationships
@EntityGraph(attributePaths = {"driver"})
Optional<Truck> findWithDriverById(Long id);

// Mapper stays simple - driver already loaded
@Mapper(componentModel = "spring", uses = DriverMapper.class)
public interface TruckMapper {
    TruckResponse toResponse(Truck truck);  // driver is available
}
```

### ✅ Solution 3: Manual Mapping for Specific Fields
```java
@Mapper(componentModel = "spring", uses = DriverMapper.class)
public interface TruckMapper {
    
    @Mapping(target = "driverName", expression = "java(truck.getDriver() != null ? truck.getDriver().getName() : null)")
    TruckResponse toResponse(Truck truck);
    
    // Or use @AfterMapping for complex logic
    @AfterMapping
    default void enrichResponse(Truck truck, @MappingTarget TruckResponse response) {
        if (truck.getDriver() != null) {
            response.setDriverName(truck.getDriver().getName());
        }
    }
}
```

## MapStruct Best Practices

### 1. Explicit Ignore for Non-Mapped Fields
```java
@Mapping(target = "id", ignore = true)
@Mapping(target = "tenantId", ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "version", ignore = true)
Truck toEntity(CreateTruckRequest request);
```

### 2. Separate Mappers for Different Directions
```java
// Creation: request → entity (ignores generated fields)
@Mapper(componentModel = "spring")
public interface TruckCreateMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Truck toEntity(CreateTruckRequest request);
}

// Update: request → existing entity (ignores nulls)
@Mapper(componentModel = "spring", 
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TruckUpdateMapper {
    void updateEntity(@MappingTarget Truck truck, UpdateTruckRequest request);
}

// Response: entity → response DTO
@Mapper(componentModel = "spring", uses = {AddressMapper.class, DriverMapper.class})
public interface TruckResponseMapper {
    TruckResponse toResponse(Truck truck);
    List<TruckListResponse> toListResponse(List<Truck> trucks);
}
```

### 3. Custom Type Conversions
```java
@Mapper(componentModel = "spring")
public interface TruckMapper {

    @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
    TruckResponse toResponse(Truck truck);

    @Named("statusToString")
    default String mapStatus(TruckStatus status) {
        return status != null ? status.name() : null;
    }

    @Named("stringToStatus")
    default TruckStatus mapStatus(String status) {
        return status != null ? TruckStatus.valueOf(status) : null;
    }
}
```

### 4. Collection Mapping with Stream API
```java
@Mapper(componentModel = "spring")
public interface TruckMapper {
    
    // MapStruct handles this automatically
    List<TruckListResponse> toListResponse(List<Truck> trucks);
    
    // For Set → List
    @Mapping(target = "assignments", source = "assignments")
    TruckWithAssignmentsResponse toWithAssignmentsResponse(Truck truck);
}
```

## Configuration in pom.xml / build.gradle

```xml
<!-- Maven -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

```gradle
// Gradle
implementation 'org.mapstruct:mapstruct:1.5.5.Final'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
// For Kotlin:
// kapt 'org.mapstruct:mapstruct-processor:1.5.5.Final'
```

## IDE Integration

Ensure annotation processing is enabled:
- **IntelliJ**: Settings → Build → Compiler → Annotation Processors → Enable
- **Eclipse**: Project Properties → Java Compiler → Annotation Processing → Enable

## Checklist Before Committing Mapper

- [ ] `unmappedTargetPolicy = ReportingPolicy.ERROR` (catches missing mappings)
- [ ] No lazy relationships accessed in mapping (check generated code)
- [ ] Explicit `@Mapping(ignore = true)` for non-mapped entity fields
- [ ] Separate mappers for create/update/response when mappings differ
- [ ] Nested mappers declared in `uses` clause
- [ ] Null handling strategy appropriate for each mapper type