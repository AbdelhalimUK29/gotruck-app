# GoTruck - Multi-Tenancy Fleet Management Platform

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](infrastructure/docker-compose.yml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14.x-black.svg)](https://nextjs.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-orange.svg)](https://www.rabbitmq.com/)

---

## Overview

**GoTruck** is a multi-tenancy fleet management platform where each user can onboard, create their company, and independently manage their operational space — including employees and rental agencies — with complete data isolation from other tenants.

### Core Capabilities

- **Multi-Tenancy**: Each company operates in a fully isolated environment with dedicated data partitions
- **Self-Service Onboarding**: Users register, create their company profile, and configure settings independently
- **Employee Management**: Create and manage company employees with role-based access
- **Rental Agency Management**: Manage rental agencies (vehicles, contracts, availability) per tenant
- **Feature-Based Access Control**: Granular permissions enabling/disabling features per subscription tier
- **Async Event Processing**: RabbitMQ for decoupled, scalable service communication

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
                        GoTruck Platform                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │   Frontend   │    │   Backend    │    │ Infrastructure│      │
│  │  (Next.js)   │◄──►│ (Spring Boot)│◄──►│   (Docker)    │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│         │                   │                   │                │
│         ▼                   ▼                   ▼                │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Shared Services                         │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────┐  │   │
│  │  │PostgreSQL│  │  Redis  │  │ MinIO   │  │ RabbitMQ    │  │   │
│  │  │ (Data)  │  │ (Cache) │  │(Storage)│  │ (Messaging) │  │   │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
gotruck-app/
├── backend/                 # Spring Boot API
│   └── goTruck/
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/com/gotruck/
│       │   │   │   ├── config/          # Configuration classes
│       │   │   │   ├── controller/      # REST controllers
│       │   │   │   ├── service/         # Business logic
│       │   │   │   ├── repository/      # Data access layer
│       │   │   │   ├── model/           # Domain entities (Company, Employee, RentalAgency, User)
│       │   │   │   ├── dto/             # Data transfer objects
│       │   │   │   ├── security/        # Auth & multi-tenancy context
│       │   │   │   ├── event/           # RabbitMQ event handling
│       │   │   │   └── util/            # Utilities
│       │   │   └── resources/
│       │   └── test/
│       ├── pom.xml
│       └── Dockerfile
│
├── frontend/                # Next.js Application
│   └── go-truck/
│       ├── src/
│       │   ├── app/                   # App Router pages
│       │   │   ├── (auth)/            # Login, register, onboarding
│       │   │   ├── (dashboard)/       # Tenant dashboard
│       │   │   │   ├── employees/     # Employee management
│       │   │   │   └── rentals/       # Rental agency management
│       │   ├── components/            # React components
│       │   ├── lib/                   # Utilities & configs
│       │   ├── hooks/                 # Custom React hooks
│       │   ├── services/              # API clients
│       │   ├── types/                 # TypeScript types
│       │   └── styles/                # Global styles
│       ├── package.json
│       ├── next.config.ts
│       └── Dockerfile
│
├── infrastructure/            # Docker & Deployment
│   ├── docker-compose.yml          # Local development stack
│   ├── nginx/                      # Reverse proxy config
│   ├── postgres/                   # Database init scripts
│   ├── redis/                      # Redis configuration
│   ├── minio/                      # MinIO bucket policies
│   └── rabbitmq/                   # RabbitMQ definitions
│
├── .gitignore
├── README.md
└── LICENSE
```

---

## Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Backend** | Spring Boot 3.x | REST API, Business Logic |
| **Backend** | Spring Security 6.x | JWT Auth, Multi-tenancy Context |
| **Backend** | Spring Data JPA | Data Persistence (PostgreSQL) |
| **Backend** | RabbitMQ Client | Async Event Publishing/Consuming |
| **Frontend** | Next.js 14 (App Router) | React Framework |
| **Frontend** | React 18 + TypeScript | UI with Type Safety |
| **Frontend** | Tailwind CSS | Styling |
| **Database** | PostgreSQL 15 | Primary Data Store (Schema-per-tenant) |
| **Cache** | Redis 7 | Session Store, Caching, Rate Limiting |
| **Storage** | MinIO | S3-Compatible Document/Asset Storage |
| **Messaging** | RabbitMQ 3.x | Event-Driven Async Processing |
| **Container** | Docker / Docker Compose | Containerization & Orchestration |

---

## Multi-Tenancy Implementation

### Tenant Isolation Strategy

GoTruck uses **Schema-Based Multi-Tenancy** with a shared PostgreSQL database:

- **Tenant Identification**: Each request carries `X-Tenant-ID` header (extracted from JWT claim)
- **Data Isolation**: Row-level security policies + schema-per-tenant enforce boundaries at DB level
- **Connection Routing**: HikariCP routes connections to the correct tenant schema
- **Cache Namespacing**: Redis keys prefixed with `tenant:{id}:` for complete isolation

### Feature-Based Access Control

Each tenant has a configurable feature matrix:

| Feature | Description |
|---------|-------------|
| `employee_management` | Create, update, deactivate employees |
| `rental_agency_management` | Manage rental agencies, vehicles, contracts |
| `analytics` | Operational dashboards & reports |
| `api_access` | Programmatic API access for integrations |
| `document_storage` | MinIO-backed document uploads |

---

## Async Processing with RabbitMQ

### Event-Driven Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Producer   │────►│  RabbitMQ   │────►│  Consumer   │
│ (Services)  │     │  Exchange   │     │ (Workers)   │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         ┌─────────┐  ┌─────────┐  ┌─────────┐
         │ Queue   │  │ Queue   │  │ Queue   │
         │(Email)  │  │(Notify) │  │(Audit)  │
         └─────────┘  └─────────┘  └─────────┘
```

### Core Events Published

| Event | Triggered By | Consumers |
|-------|--------------|-----------|
| `tenant.created` | Onboarding completion | Notification, Audit, Provisioning |
| `employee.created` | Employee added | Notification, Access Provisioning |
| `rental_agency.created` | New agency registered | Notification, Catalog Sync |
| `vehicle.assigned` | Vehicle linked to agency | Tracking, Availability Sync |

---

## Getting Started

### Prerequisites

- Docker 24+ & Docker Compose 2+
- JDK 21+ (local backend dev)
- Node.js 20+ (local frontend dev)

### Quick Start (Docker)

```bash
# Clone & enter
git clone <repo-url>
cd gotruck-app

# Start all services
cd infrastructure
docker-compose up -d

# Verify
docker-compose ps

# Access
# Frontend:      http://localhost:3000
# Backend API:   http://localhost:8080/api
# Swagger UI:    http://localhost:8080/swagger-ui.html
# RabbitMQ Mgmt: http://localhost:15672 (guest/guest)
# MinIO Console: http://localhost:9001 (minioadmin/minioadmin)
```

### Local Development

**Backend**
```bash
cd backend/goTruck
./mvnw spring-boot:run
# Or with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Frontend**
```bash
cd frontend/go-truck
npm install
npm run dev
# Build: npm run build && npm run start
```

---

## Configuration

### Backend (Environment Variables)

```yaml
# Database
SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/gotruck
SPRING_DATASOURCE_USERNAME: postgres
SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}

# Redis
SPRING_REDIS_HOST: localhost
SPRING_REDIS_PORT: 6379

# RabbitMQ
SPRING_RABBITMQ_HOST: localhost
SPRING_RABBITMQ_PORT: 5672
SPRING_RABBITMQ_USERNAME: guest
SPRING_RABBITMQ_PASSWORD: guest

# MinIO
MINIO_ENDPOINT: http://localhost:9000
MINIO_ACCESS_KEY: minioadmin
MINIO_SECRET_KEY: minioadmin
MINIO_BUCKET: gotruck-documents

# JWT
JWT_SECRET: ${JWT_SECRET}
JWT_EXPIRATION: 86400000

# Multi-Tenancy
TENANCY_RESOLVER_HEADER: X-Tenant-ID
```

### Frontend (`.env.local`)

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
NEXT_PUBLIC_MINIO_URL=http://localhost:9000
```

---

## API Usage

### Authentication & Onboarding

```bash
# Register user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "owner@company.com", "password": "secret", "companyName": "Acme Logistics"}'

# Login (returns JWT + tenantId)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "owner@company.com", "password": "secret"}'
```

### Tenant-Scoped Requests

All authenticated requests require the tenant context header:

```bash
# List employees (tenant-scoped)
curl -X GET http://localhost:8080/api/v1/employees \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "X-Tenant-ID: <tenant-id-from-login>"

# Create rental agency
curl -X POST http://localhost:8080/api/v1/rental-agencies \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "X-Tenant-ID: <tenant-id>" \
  -H "Content-Type: application/json" \
  -d '{"name": "City Rentals", "address": "123 Main St", "contactEmail": "ops@cityrentals.com"}'
```

---

## Testing

```bash
# Backend
cd backend/goTruck
./mvnw test
./mvnw verify

# Frontend
cd frontend/go-truck
npm run test
npm run test:coverage
npm run test:e2e
```

---

## Deployment

### Production (Docker Compose)

```bash
cd infrastructure
docker-compose -f docker-compose.prod.yml up -d --build
```

### Kubernetes

Helm charts located at `infrastructure/helm/gotruck` (coming soon).

---

## Security

- **Secrets**: Injected via Docker secrets / K8s secrets (never in code)
- **TLS**: Nginx reverse proxy with Let's Encrypt
- **Rate Limiting**: Per-tenant limits at API Gateway
- **Audit Logging**: All tenant actions logged for compliance
- **Data Encryption**: At-rest encryption for PII and financial data

---

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/your-feature`
3. Commit with conventional commits: `git commit -m 'feat: add rental agency CRUD'`
4. Push and open Pull Request

### Commit Types

| Type | Description |
|------|-------------|
| `feat` | New feature (employee, rental agency, etc.) |
| `fix` | Bug fix |
| `refactor` | Code restructuring |
| `docs` | Documentation updates |
| `test` | Test additions |
| `chore` | Maintenance tasks |

---

## License

MIT License - see [LICENSE](LICENSE) for details.

---

## Support

- **Issues**: GitHub Issues
- **Discussions**: GitHub Discussions
- **Email**: support@gotruck.io

---

*Built for multi-tenant fleet & rental operations*