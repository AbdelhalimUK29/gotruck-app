# Complete Data Flow Checklist

**Before implementing ANY endpoint, reason through these 17 questions.** This prevents rework and ensures production-quality APIs.

---

## The 17 Questions

### 1. Who can call this endpoint?
- [ ] Public (no auth)
- [ ] Authenticated users only
- [ ] Specific roles: `ADMIN`, `DISPATCHER`, `DRIVER`
- [ ] Specific permissions: `TRUCK_READ`, `TRUCK_CREATE`
- [ ] System-to-system (API key, client credentials)

### 2. What input does it accept?
- [ ] Request body (DTO)
- [ ] Path variables
- [ ] Query parameters
- [ ] Headers
- [ ] Multipart file upload

### 3. How is the input validated?
- [ ] Syntactic: `@Valid` + Bean Validation on DTO
- [ ] Path/query params: `@Validated` + constraints on controller method
- [ ] Custom validators for cross-field rules
- [ ] Business validation in service (separate layer)

### 4. What authorization rules apply?
- [ ] Role-based: `hasAuthority('TRUCK_READ')`
- [ ] Resource ownership: user owns the truck
- [ ] Tenant membership: user belongs to truck's tenant
- [ ] Permission hierarchy: ADMIN > DISPATCHER > DRIVER
- [ ] Custom policy: `authService.requirePermission(Permission.TRUCK_READ, truck)`

### 5. Which tenant owns the resource?
- [ ] Extracted from JWT token (Keycloak `tenant_id` claim)
- [ ] Set in `TenantContext` via filter
- [ ] All repository queries include `tenantId` filter
- [ ] No cross-tenant data access possible

### 6. What data is actually required by the consumer?
- [ ] List view: `id`, `name`, `type`, `status`, `createdAt`
- [ ] Detail view: all above + `description`, `address`, `driver`, `assignments`
- [ ] Nested: only specific related fields needed
- [ ] **Not**: internal IDs, audit fields, tenantId, version, soft-delete flags

### 7. Which database query will retrieve it?
- [ ] Projection query (DTO directly) → Best for reads
- [ ] EntityGraph with specific paths → When entity needed for write
- [ ] Fetch join JPQL → Complex relationships
- [ ] Repository method with derived query → Simple filters
- [ ] Native query → Complex SQL only

### 8. How many SQL queries will execute?
- [ ] Target: **1-2 queries maximum**
- [ ] List + count = 2 queries (acceptable)
- [ ] N+1? **Fix before committing**

### 9. Could this create an N+1 problem?
- [ ] Does response include to-many relationships?
- [ ] Does MapStruct/Jackson access lazy relationships?
- [ ] Solution: Projection, EntityGraph, fetch join, or batch fetching

### 10. What DTO should be returned?
- [ ] `XListResponse` for collections
- [ ] `XResponse` for single resource
- [ ] `XWithYResponse` for nested resources
- [ ] Dedicated DTO per use case (create ≠ update ≠ response)

### 11. How will MapStruct perform the mapping?
- [ ] Entity → Response DTO (with `uses` for nested mappers)
- [ ] Request DTO → Entity (ignore generated fields)
- [ ] Update DTO → Entity (ignore nulls, ignore generated fields)
- [ ] **No lazy access** in mapping - verify generated code

### 12. What happens when resource does not exist?
- [ ] Return 404 with `NOT_FOUND` code
- [ ] Consistent error format: `{code, message, errors[], traceId}`
- [ ] No stack trace in response

### 13. What happens when user is unauthorized?
- [ ] 401 if not authenticated
- [ ] 403 if authenticated but no permission
- [ ] Generic message (no info leakage)

### 14. What happens when validation/business rules fail?
- [ ] 400 for syntactic validation (VALIDATION_ERROR)
- [ ] 422 for business rules (INVALID_STATE_TRANSITION, etc.)
- [ ] 409 for conflicts (DUPLICATE_RESOURCE, OPTIMISTIC_LOCK)
- [ ] Field-level errors for validation

### 15. Does the endpoint need pagination?
- [ ] Collections: **always** paginate
- [ ] Default: page=0, size=20
- [ ] Max size: 100
- [ ] Sort: allow `sort=field,direction`
- [ ] Filter: dedicated filter DTO for complex queries

### 16. Does the implementation expose unnecessary data?
- [ ] No `tenantId`, `createdBy`, `version`, `deleted` in responses
- [ ] No full entity graphs when summary suffices
- [ ] No passwords, tokens, internal flags
- [ ] No bidirectional relationship cycles

### 17. Is there a simpler, more maintainable implementation?
- [ ] Can I use a projection instead of entity + mapping?
- [ ] Can I combine queries?
- [ ] Is the service doing too much? Split?
- [ ] Am I reusing existing mappers/repositories/exceptions?
- [ ] Does this follow existing project conventions?

---

## Implementation Order

```
1. Define DTOs (request + response)
2. Add repository query (projection preferred)
3. Add MapStruct mapper (if entity needed)
4. Implement service method (business logic + auth + tenant)
5. Add controller endpoint (validation + HTTP mapping)
6. Add global exception handling (if new exception types)
7. Write tests (happy path + auth + validation + tenant isolation)
8. Verify SQL queries (enable SQL logging, check count)
9. Review against this checklist
```

## Quick Reference Card

| Endpoint Type | Typical DTOs | Query Strategy | Auth |
|---------------|--------------|----------------|------|
| `GET /resources` | `ResourceFilterRequest` → `Page<ResourceListResponse>` | Projection + pagination | `READ` |
| `GET /resources/{id}` | → `ResourceResponse` | Projection or EntityGraph | `READ` + ownership |
| `POST /resources` | `CreateResourceRequest` → `ResourceResponse` | Entity save | `CREATE` |
| `PUT /resources/{id}` | `UpdateResourceRequest` → `ResourceResponse` | Entity load + update | `UPDATE` + ownership |
| `PATCH /resources/{id}` | `PatchResourceRequest` → `ResourceResponse` | Entity load + partial update | `UPDATE` + ownership |
| `DELETE /resources/{id}` | → 204 | Entity load + delete | `DELETE` + ownership |

---

**Remember**: These practices are the DEFAULT. Apply them automatically. But inspect the codebase first — if existing conventions differ, adapt rather than create parallel patterns.