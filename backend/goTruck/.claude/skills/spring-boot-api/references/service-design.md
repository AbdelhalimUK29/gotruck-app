# Service Layer Design

## Core Principle

**Services represent meaningful business operations.** They orchestrate repositories, enforce business rules, and handle authorization — not just forward repository calls.

## Service Structure

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // Default: read-only
public class TruckService {
    private final TruckRepository truckRepository;
    private final DriverRepository driverRepository;
    private final TruckMapper truckMapper;
    private final AuthorizationService authService;
    private final TenantContext tenantContext;
    private final EventPublisher eventPublisher;

    // ===== READ OPERATIONS =====
    public TruckResponse getById(Long id) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));
        
        authService.requirePermission(Permission.TRUCK_READ, truck);
        return truckMapper.toResponse(truck);
    }

    public Page<TruckListResponse> list(TruckFilterRequest filter, Pageable pageable) {
        return truckRepository.findListResponsesByTenantWithFilters(
            tenantContext.getTenantId(), filter, pageable);
    }

    // ===== WRITE OPERATIONS (explicit @Transactional) =====
    @Transactional
    public TruckResponse create(CreateTruckRequest request) {
        // 1. Validate business rules
        validateCreateRequest(request);

        // 2. Map & enrich
        Truck truck = truckMapper.toEntity(request);
        truck.setTenantId(tenantContext.getTenantId());
        truck.setCreatedBy(getCurrentUserId());

        // 3. Persist
        Truck saved = truckRepository.save(truck);

        // 4. Publish domain event (after commit)
        eventPublisher.publish(new TruckCreatedEvent(saved.getId(), saved.getTenantId()));

        return truckMapper.toResponse(saved);
    }

    @Transactional
    public TruckResponse update(Long id, UpdateTruckRequest request) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));

        authService.requirePermission(Permission.TRUCK_UPDATE, truck);
        validateUpdateRequest(truck, request);

        truckMapper.updateEntity(truck, request);
        truck.setUpdatedBy(getCurrentUserId());

        return truckMapper.toResponse(truck);
    }

    @Transactional
    public void delete(Long id) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));

        authService.requirePermission(Permission.TRUCK_DELETE, truck);
        
        // Soft delete preferred
        truck.setDeleted(true);
        truck.setDeletedAt(LocalDateTime.now());
        
        eventPublisher.publish(new TruckDeletedEvent(truck.getId(), truck.getTenantId()));
    }

    // ===== BUSINESS OPERATIONS (not CRUD) =====
    @Transactional
    public TruckResponse assignDriver(Long truckId, Long driverId) {
        Truck truck = truckRepository.findWithDriverByIdAndTenantId(truckId, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", truckId));
        
        Driver driver = driverRepository.findByIdAndTenantId(driverId, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));

        authService.requirePermission(Permission.TRUCK_ASSIGN_DRIVER, truck);

        // Business rules
        if (truck.getDriver() != null) {
            throw new BusinessRuleViolationException(
                "TRUCK_ALREADY_ASSIGNED", "Truck already has a driver assigned");
        }
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            throw new BusinessRuleViolationException(
                "DRIVER_NOT_AVAILABLE", "Driver is not available");
        }

        // Perform assignment
        truck.setDriver(driver);
        driver.setStatus(DriverStatus.ASSIGNED);
        
        eventPublisher.publish(new TruckDriverAssignedEvent(truck.getId(), driver.getId()));
        
        return truckMapper.toResponse(truck);
    }

    @Transactional
    public TruckResponse releaseDriver(Long truckId) {
        Truck truck = truckRepository.findWithDriverByIdAndTenantId(truckId, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", truckId));

        authService.requirePermission(Permission.TRUCK_RELEASE_DRIVER, truck);

        if (truck.getDriver() == null) {
            throw new BusinessRuleViolationException(
                "NO_DRIVER_ASSIGNED", "Truck has no driver to release");
        }

        Driver driver = truck.getDriver();
        truck.setDriver(null);
        driver.setStatus(DriverStatus.AVAILABLE);

        eventPublisher.publish(new TruckDriverReleasedEvent(truck.getId(), driver.getId()));
        
        return truckMapper.toResponse(truck);
    }

    // ===== VALIDATION HELPERS =====
    private void validateCreateRequest(CreateTruckRequest request) {
        if (truckRepository.existsByLicensePlateAndTenantId(request.licensePlate(), tenantContext.getTenantId())) {
            throw new DuplicateResourceException("Truck", "licensePlate", request.licensePlate());
        }
    }

    private void validateUpdateRequest(Truck truck, UpdateTruckRequest request) {
        if (request.licensePlate() != null 
            && !request.licensePlate().equals(truck.getLicensePlate())
            && truckRepository.existsByLicensePlateAndTenantId(request.licensePlate(), tenantContext.getTenantId())) {
            throw new DuplicateResourceException("Truck", "licensePlate", request.licensePlate());
        }
    }

    private Long getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication()
            .getPrincipal();  // Assuming UserDetails with ID
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| **Passthrough Service** | Service just calls `repo.findX()` | Remove service, call repo from controller (or add value) |
| **God Service** | One service does everything | Split by aggregate/bounded context |
| **Anemic Service** | No business logic, just mapping | Move rules from controller to service |
| **Service calling service in loop** | N+1, transaction issues | Batch operations, single query |
| **Service managing HTTP** | `ResponseEntity`, `@RequestBody` in service | Keep HTTP in controller |

## Separating Concerns

### Authorization Service (Extracted)
```java
@Service
@RequiredArgsConstructor
public class AuthorizationService {
    private final PermissionEvaluator permissionEvaluator;

    public void requirePermission(Permission permission, Object resource) {
        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(), 
                resource, permission)) {
            throw new AccessDeniedException(
                "Missing permission: " + permission.name());
        }
    }

    public void requireOwnershipOrAdmin(Long resourceOwnerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = ((UserPrincipal) auth.getPrincipal()).getId();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin && !currentUserId.equals(resourceOwnerId)) {
            throw new AccessDeniedException("Not owner or admin");
        }
    }
}
```

### Domain Events (Decoupled)
```java
@Component
@RequiredArgsConstructor
public class EventPublisher {
    private final ApplicationEventPublisher publisher;

    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}

// Listener (async, after commit)
@Component
@RequiredArgsConstructor
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public class TruckEventListener {
    private final NotificationService notifications;

    public void handle(TruckCreatedEvent event) {
        notifications.notifyTenantAdmins(event.tenantId(), "Truck created: " + event.truckId());
    }
}
```

## Service Design Checklist

- [ ] **Single responsibility**: Service focuses on one aggregate/domain
- [ ] **Meaningful operations**: Methods are business verbs (`assignDriver`, not `updateDriverId`)
- [ ] **Authorization in service**: Every mutating/reading op checks permissions
- [ ] **Tenant isolation**: All queries scoped to tenant
- [ ] **Validation split**: Syntactic (DTO) + Business (service)
- [ ] **Transactions at boundary**: `@Transactional` on write methods, `readOnly` on reads
- [ ] **Events for side effects**: Notifications, audit, integrations after commit
- [ ] **No HTTP types**: No `ResponseEntity`, `HttpServletRequest`, `@RequestBody`
- [ ] **No persistence types in signature**: No `Pageable` in public API (use `PaginationRequest`)
- [ ] **Testable**: Dependencies injected, no static calls

## When to Split Services

| Trigger | Action |
|---------|--------|
| Service > 500 lines | Split by aggregate |
| Methods > 50 lines | Extract to private methods or new service |
| Too many injected repos (>5) | Split by entity group |
| Cross-domain logic | Create domain service |
| Different transaction boundaries | Separate services |

## Testing Services

```java
@ExtendWith(MockitoExtension.class)
class TruckServiceTest {

    @Mock TruckRepository truckRepository;
    @Mock DriverRepository driverRepository;
    @Mock TruckMapper truckMapper;
    @Mock AuthorizationService authService;
    @Mock TenantContext tenantContext;
    @Mock EventPublisher eventPublisher;

    @InjectMocks TruckService truckService;

    @Test
    void shouldCreateTruckSuccessfully() {
        // Given
        when(tenantContext.getTenantId()).thenReturn(1L);
        CreateTruckRequest request = new CreateTruckRequest("Test", TruckType.FLATBED, ...);
        Truck truck = new Truck();
        Truck saved = new Truck();
        saved.setId(100L);
        TruckResponse response = new TruckResponse(100L, "Test", ...);

        when(truckRepository.existsByLicensePlateAndTenantId(any(), any())).thenReturn(false);
        when(truckMapper.toEntity(request)).thenReturn(truck);
        when(truckRepository.save(truck)).thenReturn(saved);
        when(truckMapper.toResponse(saved)).thenReturn(response);

        // When
        TruckResponse result = truckService.create(request);

        // Then
        assertThat(result.id()).isEqualTo(100L);
        verify(authService).requirePermission(Permission.TRUCK_CREATE, null); // or truck
        verify(eventPublisher).publish(any(TruckCreatedEvent.class));
    }

    @Test
    void shouldThrowWhenLicensePlateDuplicate() {
        when(tenantContext.getTenantId()).thenReturn(1L);
        when(truckRepository.existsByLicensePlateAndTenantId("ABC123", 1L)).thenReturn(true);

        assertThatThrownBy(() -> truckService.create(
            new CreateTruckRequest("Test", TruckType.FLATBED, ..., "ABC123")))
            .isInstanceOf(DuplicateResourceException.class);
    }
}
```