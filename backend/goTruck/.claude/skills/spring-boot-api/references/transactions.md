# Transaction Management

## Core Principle

**Use `@Transactional` at the service/application boundary.** Keep transaction boundaries intentional and minimal.

## Where to Apply `@Transactional`

### ✅ Service Layer (Correct)
```java
@Service
@RequiredArgsConstructor
public class TruckService {
    private final TruckRepository truckRepository;
    private final AssignmentRepository assignmentRepository;
    private final EventPublisher eventPublisher;

    // Read operations - readOnly = true
    @Transactional(readOnly = true)
    public TruckResponse getById(Long id) { ... }

    @Transactional(readOnly = true)
    public Page<TruckListResponse> list(Pageable pageable) { ... }

    // Write operations - default read/write
    @Transactional
    public TruckResponse create(CreateTruckRequest request) { ... }

    @Transactional
    public TruckResponse update(Long id, UpdateTruckRequest request) { ... }

    @Transactional
    public void delete(Long id) { ... }

    // Multi-step operations requiring atomicity
    @Transactional
    public TruckResponse assignDriver(Long truckId, Long driverId) {
        Truck truck = truckRepository.findByIdAndTenantId(truckId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Truck not found"));
        
        Driver driver = driverRepository.findByIdAndTenantId(driverId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));

        // Business validation
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            throw new BusinessRuleViolationException("DRIVER_NOT_AVAILABLE", "...");
        }

        // Both changes in same transaction
        truck.setDriver(driver);
        driver.setStatus(DriverStatus.ASSIGNED);
        
        // Event published after commit (use @TransactionalEventListener)
        eventPublisher.publish(new TruckAssignedEvent(truck.getId(), driver.getId()));
        
        return truckMapper.toResponse(truck);
    }
}
```

### ❌ Controller Layer (Wrong)
```java
@RestController
public class TruckController {
    
    @Transactional  // WRONG - persistence concern in web layer
    @PostMapping
    public TruckResponse create(@RequestBody CreateTruckRequest request) { ... }
}
```

### ❌ Repository Layer (Wrong)
```java
@Repository
public class TruckRepositoryImpl {
    
    @Transactional  // WRONG - Spring Data already manages this
    public Truck save(Truck truck) { ... }
}
```

## Transaction Attributes

```java
@Transactional(
    readOnly = true,                    // Read-only optimization
    propagation = Propagation.REQUIRED, // Default: join existing or create new
    isolation = Isolation.READ_COMMITTED, // Default: database default
    timeout = 30,                       // Seconds; prevent long-running transactions
    rollbackFor = {                     // Explicit rollback exceptions
        BusinessRuleViolationException.class,
        CustomException.class
    },
    noRollbackFor = {                   // Don't rollback for these
        ValidationException.class       // 4xx errors - client fault, not system fault
    }
)
public TruckResponse update(...) { ... }
```

### Propagation Behaviors

| Propagation | Behavior | Use Case |
|-------------|----------|----------|
| `REQUIRED` (default) | Join existing or create new | Most service methods |
| `REQUIRES_NEW` | Always create new, suspend current | Independent audit logging, notifications |
| `NESTED` | Savepoint within current transaction | Partial rollback within larger transaction |
| `NOT_SUPPORTED` | Execute without transaction | Read-only reporting, external calls |
| `NEVER` | Fail if transaction exists | Explicitly non-transactional code |

### Read-Only Transactions

```java
// Use for ALL read operations
@Transactional(readOnly = true)
public TruckResponse getById(Long id) { ... }

@Transactional(readOnly = true)
public Page<TruckListResponse> list(Pageable pageable) { ... }

@Transactional(readOnly = true)
public List<TruckSummary> findAllSummaries() { ... }
```

**Benefits of `readOnly = true`:**
- Hibernate disables dirty checking (performance)
- Database can use read replicas / read-only connections
- Prevents accidental writes
- Clear intent for code reviewers

## Transaction Boundaries & Lazy Loading

### Problem: Lazy Loading Outside Transaction
```java
@Transactional
public TruckResponse getWithDriver(Long id) {
    Truck truck = truckRepository.findById(id).orElseThrow();  // Loaded in TX
    return truckMapper.toResponse(truck);  // Mapping accesses truck.getDriver().getName()
}  // TX commits here - session closed

// Later in mapper (or Jackson serialization):
truck.getDriver().getName()  // ❌ LazyInitializationException!
```

### Solutions

#### 1. Load Required Data Within Transaction (Best)
```java
@Transactional(readOnly = true)
public TruckResponse getWithDriver(Long id) {
    // EntityGraph or fetch join loads driver in same query
    Truck truck = truckRepository.findWithDriverById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Truck not found"));
    
    return truckMapper.toResponse(truck);  // Driver already loaded
}
```

#### 2. Keep Transaction Open During Mapping (Acceptable for Simple Cases)
```java
@Transactional(readOnly = true)
public TruckResponse getWithDriver(Long id) {
    Truck truck = truckRepository.findById(id).orElseThrow();
    return truckMapper.toResponse(truck);  // Mapping happens inside TX
}
```

#### 3. Use DTO Projections (Best - Avoids Entity Entirely)
```java
@Transactional(readOnly = true)
public TruckResponse getWithDriver(Long id) {
    return truckRepository.findResponseById(id)  // Returns DTO directly
        .orElseThrow(() -> new ResourceNotFoundException("Truck not found"));
}
```

## Multi-Step Operations

### All-or-Nothing Pattern
```java
@Transactional
public OrderResponse createOrder(CreateOrderRequest request) {
    // 1. Validate all preconditions
    validateCustomer(request.customerId());
    validateInventory(request.items());
    
    // 2. Create order
    Order order = orderMapper.toEntity(request);
    order = orderRepository.save(order);
    
    // 3. Reserve inventory (same transaction)
    for (OrderItem item : order.getItems()) {
        inventoryService.reserve(item.getProductId(), item.getQuantity());
    }
    
    // 4. Create payment record
    paymentService.createPendingPayment(order.getId(), order.getTotal());
    
    // 5. Publish event (after commit via @TransactionalEventListener)
    eventPublisher.publish(new OrderCreatedEvent(order.getId()));
    
    return orderMapper.toResponse(order);
}
```

### Partial Rollback with Nested Transactions
```java
@Transactional
public void processBatch(List<BatchItem> items) {
    for (BatchItem item : items) {
        try {
            processSingleItem(item);  // Nested transaction - can rollback independently
        } catch (BusinessRuleViolationException ex) {
            // Log failure but continue with other items
            log.warn("Item {} failed: {}", item.getId(), ex.getMessage());
            deadLetterQueue.add(item, ex);
        }
    }
}

@Transactional(propagation = Propagation.NESTED)
public void processSingleItem(BatchItem item) { ... }
```

## Exception Handling & Rollback

### Default Rollback Behavior
- **RuntimeException & Error**: Rollback ✅
- **Checked Exceptions**: NO rollback by default ❌

### Explicit Configuration
```java
@Transactional(rollbackFor = {BusinessRuleViolationException.class, IOException.class})
public void criticalOperation() { ... }

@Transactional(noRollbackFor = {ValidationException.class, ResourceNotFoundException.class})
public void operationWithExpectedFailures() { ... }
```

## Testing Transactions

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TruckServiceTransactionTest {

    @Autowired TruckRepository truckRepository;
    @Autowired TestEntityManager entityManager;
    TruckService truckService;

    @Test
    @Transactional  // Test runs in transaction, rolls back after
    void shouldRollbackOnBusinessException() {
        // Given
        CreateTruckRequest request = new CreateTruckRequest("Test", TruckType.FLATBED, ...);
        
        // When - service throws exception
        assertThatThrownBy(() -> truckService.create(request))
            .isInstanceOf(BusinessRuleViolationException.class);
        
        // Then - no truck persisted (transaction rolled back)
        assertThat(truckRepository.count()).isZero();
    }

    @Test
    void shouldCommitOnSuccess() {
        CreateTruckRequest request = new CreateTruckRequest("Test", TruckType.FLATBED, ...);
        
        TruckResponse response = truckService.create(request);
        
        // Flush to ensure DB write
        entityManager.flush();
        
        assertThat(truckRepository.findById(response.id())).isPresent();
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| `@Transactional` on controller | Leaks persistence to web layer | Move to service |
| `@Transactional` on read method without `readOnly` | Unnecessary overhead | Add `readOnly = true` |
| Huge transaction spanning multiple services | Long locks, deadlock risk | Split into smaller units |
| No transaction on multi-step write | Partial updates on failure | Add `@Transactional` |
| Catching exception to prevent rollback | Hides failures | Let exception propagate |
| `@Transactional` on private method | Spring ignores it (proxy-based) | Make public or use AspectJ |