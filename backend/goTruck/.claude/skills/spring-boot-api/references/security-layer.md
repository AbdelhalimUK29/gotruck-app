# Security Layer Implementation

## Security Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. FILTER CHAIN (Spring Security)                               │
│    - JWT token validation (Keycloak)                            │
│    - Authentication object creation                             │
│    - TenantContext population from token                        │
├─────────────────────────────────────────────────────────────────┤
│ 2. METHOD SECURITY (@PreAuthorize)                              │
│    - Role/permission checks at controller                       │
│    - Base authentication requirement                            │
├─────────────────────────────────────────────────────────────────┤
│ 3. SERVICE-LEVEL AUTHORIZATION                                  │
│    - Resource-level permissions                                 │
│    - Ownership verification                                     │
│    - Tenant isolation enforcement                               │
├─────────────────────────────────────────────────────────────────┤
│ 4. REPOSITORY-LEVEL TENANT FILTERING                            │
│    - All queries scoped to tenantId                             │
│    - Hibernate @Filter for automatic application                │
└─────────────────────────────────────────────────────────────────┘
```

## 1. JWT Token Processing (Keycloak)

### Security Configuration
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // Enables @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.example.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

### JWT Authentication Converter
```java
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String tenantId = jwt.getClaimAsString("tenant_id");
        String userId = jwt.getSubject();
        
        UserPrincipal principal = new UserPrincipal(userId, tenantId, authorities);
        return new JwtAuthenticationToken(jwt, authorities, principal);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("realm_access.roles");
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            authorities.addAll(roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                .toList());
        }
        if (permissions != null) {
            authorities.addAll(permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList());
        }
        return authorities;
    }
}
```

### User Principal
```java
public record UserPrincipal(
    String userId,
    String tenantId,
    Collection<? extends GrantedAuthority> authorities
) implements Principal {
    public Long getUserIdAsLong() { return Long.valueOf(userId); }
    public Long getTenantIdAsLong() { return Long.valueOf(tenantId); }
    public boolean hasRole(String role) {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role.toUpperCase()));
    }
    public boolean hasPermission(String permission) {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals(permission));
    }
}
```

## 2. Method Security (Controller)

```java
@RestController
@RequestMapping("/api/v1/trucks")
@RequiredArgsConstructor
// Base: authenticated users only
@PreAuthorize("isAuthenticated()")
public class TruckController {
    private final TruckService truckService;

    @GetMapping
    @PreAuthorize("hasAuthority('TRUCK_READ')")
    public ResponseEntity<PageResponse<TruckListResponse>> list(...) { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRUCK_READ')")
    public ResponseEntity<TruckResponse> get(@PathVariable Long id) { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('TRUCK_CREATE')")
    public ResponseEntity<TruckResponse> create(@Valid @RequestBody CreateTruckRequest request) { ... }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRUCK_UPDATE')")
    public ResponseEntity<TruckResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateTruckRequest request) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TRUCK_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) { ... }

    // Action endpoints
    @PostMapping("/{id}/assign-driver")
    @PreAuthorize("hasAuthority('TRUCK_ASSIGN_DRIVER')")
    public ResponseEntity<TruckResponse> assignDriver(@PathVariable Long id, @RequestBody AssignDriverRequest request) { ... }
}
```

## 3. Service-Level Authorization

### Permission Enum
```java
public enum Permission {
    TRUCK_READ,
    TRUCK_CREATE,
    TRUCK_UPDATE,
    TRUCK_DELETE,
    TRUCK_ASSIGN_DRIVER,
    TRUCK_RELEASE_DRIVER,
    DRIVER_READ,
    DRIVER_CREATE,
    DRIVER_UPDATE,
    DRIVER_DELETE,
    ASSIGNMENT_READ,
    ASSIGNMENT_CREATE,
    // Admin permissions
    ADMIN_ACCESS
}
```

### Authorization Service
```java
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    public void requirePermission(Permission permission, Object resource) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        
        // Check permission
        if (!principal.hasPermission(permission.name())) {
            throw new AccessDeniedException("Missing permission: " + permission.name());
        }
        
        // Check tenant isolation for resource
        if (resource instanceof HasTenantId tenantResource) {
            if (!principal.getTenantIdAsLong().equals(tenantResource.getTenantId())) {
                throw new AccessDeniedException("Cross-tenant access denied");
            }
        }
    }

    public void requireOwnershipOrAdmin(Long resourceOwnerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        
        boolean isAdmin = principal.hasRole("ADMIN");
        boolean isOwner = principal.getUserIdAsLong().equals(resourceOwnerId);
        
        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("Not owner or admin");
        }
    }

    public void requireTenantAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        
        if (!principal.hasRole("TENANT_ADMIN") && !principal.hasRole("ADMIN")) {
            throw new AccessDeniedException("Tenant admin required");
        }
    }
}
```

### Usage in Service
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TruckService {
    private final AuthorizationService authService;
    private final TenantContext tenantContext;

    public TruckResponse getById(Long id) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));

        // Resource-level authorization
        authService.requirePermission(Permission.TRUCK_READ, truck);
        
        return truckMapper.toResponse(truck);
    }

    @Transactional
    public TruckResponse update(Long id, UpdateTruckRequest request) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));

        // Check UPDATE permission + tenant
        authService.requirePermission(Permission.TRUCK_UPDATE, truck);

        truckMapper.updateEntity(truck, request);
        return truckMapper.toResponse(truck);
    }

    @Transactional
    public void delete(Long id) {
        Truck truck = truckRepository.findByIdAndTenantId(id, tenantContext.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck", id));

        // Ownership check
        authService.requireOwnershipOrAdmin(truck.getCreatedBy());
        
        truckRepository.delete(truck);
    }
}
```

## 4. Repository-Level Tenant Isolation

### Hibernate Filter (Automatic)
```java
@Configuration
@RequiredArgsConstructor
public class HibernateConfig {
    private final TenantContext tenantContext;

    @Bean
    public SessionFactoryCustomizer tenantFilterCustomizer() {
        return sessionFactory -> {
            sessionFactory.addFilter("tenantFilter", session -> {
                Long tenantId = tenantContext.getTenantId();
                if (tenantId != null) {
                    return session.enableFilter("tenantFilter")
                        .setParameter("tenantId", tenantId);
                }
                return session; // No filter if no tenant (admin?)
            });
        };
    }
}

// On each tenant-scoped entity
@Entity
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "long"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "trucks")
public class Truck extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    // ...
}
```

### Explicit Tenant in Queries (Also Required)
```java
public interface TruckRepository extends JpaRepository<Truck, Long> {
    // ALWAYS include tenantId - defense in depth
    Optional<Truck> findByIdAndTenantId(Long id, Long tenantId);
    
    Page<Truck> findByTenantId(Long tenantId, Pageable pageable);
    
    boolean existsByIdAndTenantId(Long id, Long tenantId);
    
    @Query("SELECT t FROM Truck t WHERE t.tenantId = :tenantId AND t.status = :status")
    Page<Truck> findByTenantIdAndStatus(Long tenantId, TruckStatus status, Pageable pageable);
}
```

## 5. Tenant Context Management

```java
@Component
public class TenantContext {
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        currentTenant.set(tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = currentTenant.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set. Request may be unauthenticated.");
        }
        return tenantId;
    }

    public static Optional<Long> getOptionalTenantId() {
        return Optional.ofNullable(currentTenant.get());
    }

    public static void clear() {
        currentTenant.remove();
    }
}

// Filter to populate from JWT
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {
    private final JwtAuthConverter jwtAuthConverter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) 
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // Parse JWT (simplified - use proper JWT parser in production)
                Long tenantId = extractTenantFromToken(token);
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
```

## 6. IDOR/BOLA Prevention

### Never Trust User-Provided IDs
```java
// ❌ VULNERABLE - No ownership check
@DeleteMapping("/trucks/{id}")
public void delete(@PathVariable Long id) {
    truckRepository.deleteById(id);
}

// ✅ SECURE - Service verifies ownership
@DeleteMapping("/trucks/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    truckService.delete(id);  // Service checks auth
    return ResponseEntity.noContent().build();
}

// Service
@Transactional
public void delete(Long id) {
    Long tenantId = TenantContext.getTenantId();
    Truck truck = truckRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Truck", id));
    
    // Authorization check
    authService.requirePermission(Permission.TRUCK_DELETE, truck);
    // OR ownership
    authService.requireOwnershipOrAdmin(truck.getCreatedBy());
    
    truckRepository.delete(truck);
}
```

## 7. Mass Assignment Protection

### Dedicated Update DTO
```java
// Only fields user CAN update
public record UpdateTruckRequest(
    @Size(max = 100) String name,
    TruckType type,
    @Size(max = 500) String description,
    @Valid AddressDto address,
    TruckStatus status
    // NO: id, tenantId, createdBy, createdAt, version, deleted
) {}

// Mapper ignores nulls = can't overwrite with null
@Mapper(componentModel = "spring", 
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TruckMapper {
    void updateEntity(@MappingTarget Truck truck, UpdateTruckRequest request);
}
```

## 8. Sensitive Data Protection

### Response DTOs Exclude Internal Fields
```java
public record TruckResponse(
    Long id,
    String name,
    TruckType type,
    TruckStatus status,
    String description,
    AddressDto address,
    DriverSummaryDto driver,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
    // NO: tenantId, createdBy, updatedBy, version, deleted, deletedAt
) {}
```

### Error Messages Don't Leak Info
```java
@RestControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        // Generic message - no info about WHY access denied
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("FORBIDDEN", "Access denied", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);  // Full details in SERVER logs
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", List.of()));
    }
}
```

## 9. Rate Limiting

```java
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final Bucket4jRateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        String key = resolveKey(request);  // User ID or IP
        if (!rateLimiter.tryConsume(key)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            return;
        }
        chain.doFilter(request, response);
    }
}

// Or use Resilience4j
@Configuration
public class RateLimitConfig {
    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.ofDefaults("api");
    }
}
```

## Security Checklist for Every Endpoint

| Check | Implementation |
|-------|----------------|
| ✅ Authenticated? | `@PreAuthorize("isAuthenticated()")` on class |
| ✅ Authorized (role)? | `@PreAuthorize("hasAuthority('TRUCK_READ')")` |
| ✅ Resource permission? | `authService.requirePermission(Permission.TRUCK_READ, truck)` |
| ✅ Tenant isolation? | Repository `findByIdAndTenantId()` + Hibernate `@Filter` |
| ✅ IDOR prevented? | Never trust ID alone; verify ownership in service |
| ✅ Mass assignment? | Dedicated update DTO + MapStruct `IGNORE` nulls |
| ✅ Sensitive data? | Response DTOs exclude internal fields |
| ✅ Error messages? | Generic in response, detailed in server logs |
| ✅ Rate limited? | Filter or Resilience4j |

## Testing Security

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockBean TruckService truckService;

    @Test
    void unauthenticatedRequestShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/trucks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "TRUCK_READ")
    void authorizedUserShouldAccess() throws Exception {
        when(truckService.list(any(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/trucks"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "DRIVER_READ")  // Wrong permission
    void unauthorizedUserShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/trucks"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "TRUCK_READ")
    void tenantIsolationShouldReturn404() throws Exception {
        // Tenant A user requesting Tenant B's truck
        when(truckService.getById(999L))
            .thenThrow(new ResourceNotFoundException("Truck", 999L));
        
        mockMvc.perform(get("/api/trucks/999"))
            .andExpect(status().isNotFound());  // Not 403 - hide existence
    }
}
```