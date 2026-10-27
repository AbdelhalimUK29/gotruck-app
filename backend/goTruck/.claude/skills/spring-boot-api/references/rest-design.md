# REST API Design Conventions

## Core Principle

**Predictable, resource-oriented endpoints with standard HTTP semantics.**

## Resource Naming

| Resource | Collection | Single |
|----------|------------|--------|
| Trucks | `/api/trucks` | `/api/trucks/{id}` |
| Drivers | `/api/drivers` | `/api/drivers/{id}` |
| Assignments | `/api/assignments` | `/api/assignments/{id}` |

### Nested Resources (When Appropriate)
```java
// Assignments scoped to a truck
GET    /api/trucks/{truckId}/assignments
POST   /api/trucks/{truckId}/assignments
GET    /api/trucks/{truckId}/assignments/{id}

// NOT: /api/trucks/{truckId}/drivers (driver is independent resource)
// Use: /api/drivers?truckId={truckId} for filtering
```

### Non-Resource Operations (Actions)
```java
// Use sub-resource for state transitions
POST   /api/trucks/{id}/assign-driver
POST   /api/trucks/{id}/release-driver
POST   /api/trucks/{id}/maintenance
POST   /api/assignments/{id}/complete

// Alternative: PATCH with status field
PATCH  /api/trucks/{id}  { "status": "MAINTENANCE" }
```

## HTTP Methods

| Method | Use Case | Idempotent | Safe |
|--------|----------|------------|------|
| GET | Retrieve | Yes | Yes |
| POST | Create, actions | No | No |
| PUT | Full replacement | Yes | No |
| PATCH | Partial update | No* | No |
| DELETE | Delete | Yes | No |

*PATCH idempotency depends on implementation; prefer PUT for full updates.

## Status Codes

| Code | Use Case |
|------|----------|
| 200 | Successful GET, PUT, PATCH |
| 201 | Successful POST (creation) |
| 204 | Successful DELETE, no content |
| 400 | Invalid request (validation) |
| 401 | Unauthenticated |
| 403 | Authenticated but unauthorized |
| 404 | Resource not found |
| 409 | Conflict (duplicate, optimistic lock) |
| 422 | Business rule violation |
| 429 | Rate limited |
| 500 | Server error |
| 503 | Service unavailable |

## Request/Response Examples

### Create
```http
POST /api/trucks
Content-Type: application/json

{
  "name": "Flatbed #1",
  "type": "FLATBED",
  "description": "Heavy duty flatbed",
  "licensePlate": "ABC123",
  "address": {
    "street": "123 Main St",
    "city": "Springfield",
    "postalCode": "12345",
    "country": "USA"
  }
}

Response: 201 Created
{
  "id": 100,
  "name": "Flatbed #1",
  "type": "FLATBED",
  "status": "ACTIVE",
  "description": "Heavy duty flatbed",
  "licensePlate": "ABC123",
  "address": {...},
  "createdAt": "2026-09-25T10:30:00Z",
  "updatedAt": "2026-09-25T10:30:00Z"
}
```

### List with Pagination & Filtering
```http
GET /api/trucks?page=0&size=20&sort=name,asc&type=FLATBED&status=ACTIVE

Response: 200 OK
{
  "content": [
    {"id": 100, "name": "Flatbed #1", "type": "FLATBED", "status": "ACTIVE", "createdAt": "..."},
    {"id": 101, "name": "Flatbed #2", "type": "FLATBED", "status": "ACTIVE", "createdAt": "..."}
  ],
  "metadata": {
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3,
    "first": true,
    "last": false,
    "sort": {"fields": [{"property": "name", "direction": "asc"}]}
  }
}
```

### Get Single
```http
GET /api/trucks/100

Response: 200 OK
{
  "id": 100,
  "name": "Flatbed #1",
  "type": "FLATBED",
  "status": "ACTIVE",
  "description": "Heavy duty flatbed",
  "licensePlate": "ABC123",
  "address": {...},
  "driver": {"id": 50, "name": "John Doe", "licenseNumber": "DL12345"},
  "activeAssignments": [...],
  "createdAt": "2026-09-25T10:30:00Z",
  "updatedAt": "2026-09-25T10:30:00Z"
}
```

### Update (Full - PUT)
```http
PUT /api/trucks/100
Content-Type: application/json

{
  "name": "Flatbed #1 Updated",
  "type": "FLATBED",
  "description": "Updated description",
  "licensePlate": "ABC123",
  "address": {...}
}

Response: 200 OK
{
  "id": 100,
  "name": "Flatbed #1 Updated",
  ...
}
```

### Partial Update (PATCH)
```http
PATCH /api/trucks/100
Content-Type: application/json

{
  "status": "MAINTENANCE"
}

Response: 200 OK
{
  "id": 100,
  "status": "MAINTENANCE",
  ...
}
```

### Delete
```http
DELETE /api/trucks/100

Response: 204 No Content
```

### Error Responses
```http
Response: 400 Bad Request
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request",
  "errors": [
    {"field": "name", "message": "Name is required"},
    {"field": "licensePlate", "message": "Invalid license plate format"}
  ],
  "traceId": "abc-123",
  "timestamp": "2026-09-25T10:30:00Z"
}
```

```http
Response: 404 Not Found
{
  "code": "NOT_FOUND",
  "message": "Truck not found: 999",
  "errors": [],
  "traceId": "abc-123",
  "timestamp": "2026-09-25T10:30:00Z"
}
```

## Query Parameter Conventions

### Pagination
| Param | Default | Description |
|-------|---------|-------------|
| `page` | 0 | Zero-based page index |
| `size` | 20 | Page size (max 100) |

### Sorting
```
sort=field,direction
sort=createdAt,desc
sort=name,asc&sort=createdAt,desc  // Multi-field sort
```

Allowed sort fields per endpoint (documented in API spec).

### Filtering
```
?type=FLATBED
?status=ACTIVE&type=FLATBED
?nameContains=flatbed
?createdAfter=2026-01-01&createdBefore=2026-12-31
?driverId=50
?hasDriver=true
```

## Versioning

### URL Versioning (Required for Breaking Changes)
```
/api/v1/trucks
/api/v2/trucks
```

### Header Versioning (Alternative)
```
Accept: application/vnd.example.v1+json
```

## Headers

### Request
| Header | Purpose |
|--------|---------|
| `Authorization: Bearer <token>` | Authentication |
| `Content-Type: application/json` | Request format |
| `Accept: application/json` | Response format |
| `Idempotency-Key: <uuid>` | Idempotent POST (optional) |

### Response
| Header | Purpose |
|--------|---------|
| `Content-Type: application/json` | Response format |
| `X-Trace-Id: <uuid>` | Request correlation |
| `RateLimit-Limit: 100` | Rate limit info |
| `RateLimit-Remaining: 99` | Remaining requests |

## API Documentation

Use OpenAPI/Swagger with annotations:

```java
@RestController
@RequestMapping("/api/v1/trucks")
@Tag(name = "Trucks", description = "Truck management API")
@RequiredArgsConstructor
public class TruckController {

    @Operation(summary = "List trucks", description = "Returns paginated list of trucks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    public ResponseEntity<PageResponse<TruckListResponse>> list(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field,direction") @RequestParam(defaultValue = "createdAt,desc") String sort,
            @ParameterObject TruckFilterRequest filter) {
        ...
    }

    @Operation(summary = "Get truck by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "Not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TruckResponse> get(
            @Parameter(description = "Truck ID") @PathVariable Long id) {
        ...
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| RPC-style URLs | `/api/getTruckById?id=1` | Resource-oriented: `/api/trucks/1` |
| Verbs in URL | `/api/createTruck` | POST `/api/trucks` |
| Using GET for mutations | `/api/trucks/1/delete` | DELETE `/api/trucks/1` |
| Inconsistent naming | `/api/truck` vs `/api/trucks` | Plural nouns consistently |
| Over-nesting | `/api/trucks/1/drivers/2/assignments/3` | Max 2 levels; use filters |
| No pagination | Returns all | Always paginate collections |
| Different error formats | 400 vs 422 structure differs | Global exception handler |
| Missing 401/403 | Security gaps | Auth on every endpoint |