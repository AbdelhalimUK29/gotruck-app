---
name: spring-boot-api
description: Default engineering standards for Spring Boot REST API development in this project. Enforces clean architecture, DTO-first design, N+1 prevention, security, multi-tenancy, and production-quality patterns.
---

# Spring Boot REST API Engineering Standards

This skill defines the default practices Claude must follow when developing, modifying, reviewing, or refactoring REST APIs in this Spring Boot backend.

**These practices are the DEFAULT.** You should automatically apply them without being explicitly asked. However, do not apply rules mechanically when they conflict with existing architecture or actual feature requirements. Inspect the codebase first and adapt appropriately.

## Quick Reference

| Area | Key Principle |
|------|---------------|
| Architecture | Controller → Service → Repository → Database |
| DTOs | Never expose entities; dedicated request/response DTOs |
| Mapping | MapStruct for all Entity ↔ DTO conversions |
| Queries | Prevent N+1; use projections/fetch joins/EntityGraph |
| Security | AuthN + AuthZ + tenant isolation on every endpoint |
| Validation | Jakarta Bean Validation on all external input |
| Errors | Centralized `@RestControllerAdvice` with consistent structure |
| Transactions | `@Transactional` at service boundary, intentional boundaries |
| Testing | 80%+ coverage; test auth, tenant isolation, business rules |

## When This Skill Applies

- Creating new REST endpoints
- Modifying existing endpoints
- Reviewing API implementations
- Refactoring controller/service/repository layers
- Adding new entities or relationships that affect APIs

## Core Standards (Summary)

### 1. API Architecture
**Controller → Service → Repository → Database**
- Controllers: HTTP concerns, validation, auth context, mapping
- Services: Business logic (not in controllers!)
- Repositories: Persistence only (no business logic unless persistence-specific)
- Avoid god services/controllers

### 2. DTO-First Design
- Never expose JPA entities directly
- Dedicated DTOs: `CreateXRequest`, `UpdateXRequest`, `XResponse`
- Only return fields the consumer needs
- Exclude: IDs (when not needed), audit fields, internal fields, security-sensitive data

### 3. MapStruct Mapping
- Use MapStruct for all Entity ↔ DTO mapping
- Configure explicitly for name mismatches/nested objects
- **Never** trigger lazy loading during mapping (prevents hidden N+1)

### 4. N+1 Query Prevention (CRITICAL)
Before implementing any endpoint returning related data:
- Determine required relationships & fields
- Count expected SQL queries
- Check for lazy loading in serialization/mapping
- Use: fetch joins, EntityGraph, JPQL projections, DTO projections, batch fetching
- **Never** blindly change `LAZY` to `EAGER`

### 5. Database Query Optimization
- Retrieve only required data (avoid `SELECT *`)
- Use projections when full entity not needed
- Database-level pagination (not in-memory)
- Consider indexes for actual query patterns

### 6. Validation
- Validate ALL external input with Jakarta Bean Validation
- Distinguish: syntactic validation, business validation, authorization
- Never trust client data

### 7. Security & Multi-Tenancy
- Spring Security + Keycloak on every protected endpoint
- Verify: authentication, authorization, role/permission, resource ownership, tenant isolation
- **Server-side** tenant enforcement (never rely on frontend)
- Queries should include tenant constraints
- Prevent IDOR/BOLA: never trust user-provided IDs for ownership

### 8. Error Handling
- Centralized via `@RestControllerAdvice`
- Consistent error response structure
- Appropriate HTTP codes: 400, 401, 403, 404, 409, 422, 500
- No stack traces or internal details in responses

### 9. Transactions
- `@Transactional` at service boundary
- `readOnly = true` for read operations
- Keep boundaries intentional

### 10. REST Design
- Resource-oriented endpoints
- Predictable HTTP semantics (GET/POST/PUT/PATCH/DELETE)
- Pagination for collections: page, size, sort, filter
- No business logic in URLs

## Before Implementing Any Endpoint

Think through the complete data flow (from `references/complete-data-flow.md`):

1. Who can call this?
2. What input? How validated?
3. What authZ rules? What tenant?
4. What data actually required?
5. Which DB query? How many SQL queries?
6. N+1 risk?
7. What DTO returned? How mapped?
8. Error cases: not found, unauthorized, validation fail, business rule violation?
9. Pagination needed?
10. Unnecessary data exposed?
11. Simpler implementation exists?

## References

- `references/architecture.md` — Controller/Service/Repository separation
- `references/dto-design.md` — DTO patterns and conventions
- `references/mapstruct.md` — MapStruct configuration and anti-patterns
- `references/nplus1-prevention.md` — N+1 analysis and solutions
- `references/query-optimization.md` — Efficient database access
- `references/validation-layer.md` — Three-layer validation (syntactic, business, authorization)
- `references/security-layer.md` — Complete security implementation (JWT, Keycloak, tenant isolation, IDOR prevention)
- `references/error-handling.md` — Centralized exception handling
- `references/transactions.md` — Transaction boundaries
- `references/rest-design.md` — REST conventions
- `references/entity-design.md` — JPA entity best practices
- `references/repository-design.md` — Repository patterns
- `references/service-design.md` — Service layer principles
- `references/response-design.md` — API response structure
- `references/complete-data-flow.md` — Pre-implementation checklist (17 questions)
- `references/code-quality.md` — Naming, structure, patterns

## Behavior Expectation

When asked to implement an API:
1. **First** inspect existing project structure (architecture, DTOs, MapStruct, security, tenant model, repos, exceptions, pagination)
2. **Reuse** existing conventions — don't create parallel architecture
3. **Preserve** established patterns unless concrete reason to change
4. **Identify** N+1 risks and use appropriate query strategy
5. **Address** security vulnerabilities as part of implementation
6. **Explain** tradeoffs briefly when simplicity vs. optimization