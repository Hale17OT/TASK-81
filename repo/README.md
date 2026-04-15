# CampusStore — Inventory & Marketplace Operations Platform

**Type: Fullstack** — Spring Boot REST API backend + Thymeleaf server-rendered web interface, backed by MySQL, fully containerized with Docker Compose.

An offline, on-premises marketplace and stockroom workflow platform for school communities with strong governance, role-based access, and intelligent warehouse operations.

## Overview

CampusStore enables a school community to run a managed inventory marketplace entirely on local infrastructure — no internet required. Students browse and request items, Teachers act as department-scoped approvers, and Administrators manage the full catalog, warehouse operations, policies, and audits.

### Key Features

- **Role-based access**: Students, Teachers, Administrators with least-privilege enforcement
- **Approval workflow**: Configurable per-item governance with department-scoped teacher approval and self-approval blocking
- **Intelligent search**: Tokenized keyword matching, multi-dimensional filters, zone-distance sorting, personalization with privacy toggle
- **Warehouse operations**: ABC classification, FIFO/expiration, 3D pick paths, putaway recommendations, shadow simulation engine
- **Notification center**: In-app notifications, email outbox for offline delivery, DND windows, per-category preferences
- **Crawler observability**: Local data source ingestion with threshold alerting and failure snapshots
- **Strong security**: AES-256 PII encryption (including password hashes), BCrypt passwords, forced TLS, rate limiting with escalating lockouts
- **Immutable audit trail**: 7-year retention with cryptographic erasure for deleted users
- **100% offline**: No external service dependencies

## Prerequisites

- Docker & Docker Compose (**required** — all runtime components are containerized)

## Quick Start

```bash
MASTER_KEY_PASSPHRASE=YourSecurePassphrase2026 \
SERVER_SSL_KEY_STORE_PASSWORD=changeit \
docker-compose up --build
```

The application will be available at **https://localhost:8443**

> The TLS certificate is self-signed. Your browser will show a security warning — proceed to accept it.

## Verify System Is Running

After startup, confirm the application is healthy with these checks:

```bash
# 1. Health endpoint — should return {"status":"UP",...}
curl -k https://localhost:8443/actuator/health

# 2. Login and get session cookie
curl -k -c /tmp/campus.jar -X POST https://localhost:8443/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# Expected: {"success":true,"data":{"username":"admin","roles":["ADMIN"],...}}

# 3. List inventory (authenticated)
curl -k -b /tmp/campus.jar https://localhost:8443/api/inventory
# Expected: {"success":true,"data":{"content":[{"id":1,"name":"Arduino Uno R3 Board",...}],...}}
```

**UI verification**: Navigate to `https://localhost:8443` — you should be redirected to `/login`. Log in as `admin` / `admin123` to reach the admin dashboard.

## Default Users

| Username | Password | Role | Department |
|----------|----------|------|------------|
| admin | admin123 | Administrator | — |
| teacher1 | teacher123 | Teacher | Science |
| teacher2 | teacher123 | Teacher | Engineering |
| student1 | student123 | Student | — |
| student2 | student123 | Student | — |

> **Note**: Seed data stores raw BCrypt hashes. On first startup, `SeedDataInitializer` automatically AES-encrypts them using the runtime `MASTER_KEY_PASSPHRASE`. After startup, all password hashes are AES-256-GCM encrypted at rest.

## Test Credentials (for automated tests)

When running with `@ActiveProfiles("test")` (H2 in-memory database), the `TestDataConfig` creates these users:

| Username | Password | Role |
|----------|----------|------|
| testadmin | Admin123! | ADMIN |
| testteacher | Teacher123! | TEACHER |
| teststudent | Student123! | STUDENT |

## Testing

```bash
./run_tests.sh --docker
```

This unified script runs:
1. **Unit tests** — Domain logic, algorithms, validators, state machines
2. **Integration tests** — HTTP black-box API tests against real endpoints (no mocks)
3. **E2E tests** — Browser flows with Playwright against Docker environment

## Architecture

### Hexagonal with REST API Adapter

```
┌──────────────────────────────────────────────────┐
│              Inbound Adapters                    │
│  ┌──────────────┐    ┌───────────────────────┐   │
│  │ REST API      │    │ Thymeleaf Web         │   │
│  │ Controllers   │    │ (via InternalApiClient)│   │
│  └──────┬───────┘    └──────────┬────────────┘   │
│         │                       │                │
│         │   ┌───────────────────┘                │
│         │   │ HTTP calls to /api/**              │
│         ▼   ▼                                    │
│  ┌─────────────────────────────────────────┐     │
│  │  Domain Services (Business Logic)       │     │
│  │  Domain Models & Enums                  │     │
│  └──────┬──────────────────────────────────┘     │
│         │     Outbound Adapters                  │
│  ┌──────▼──────┐  ┌──────────┐  ┌────────────┐  │
│  │ JPA/MySQL   │  │ AES-256  │  │ Bucket4j   │  │
│  │ Persistence │  │ Encrypt  │  │ Rate Limit │  │
│  └─────────────┘  └──────────┘  └────────────┘  │
└──────────────────────────────────────────────────┘
```

The Thymeleaf web layer consumes the REST API through `InternalApiClient`, which issues real HTTP calls to the `/api/**` endpoints over loopback. This satisfies the prompt requirement for a "decoupled REST-style API consumed by the Thymeleaf frontend."

### Module Structure

```
src/main/java/com/campusstore/
├── api/           # REST controllers, DTOs, validators
├── web/           # Thymeleaf page controllers + InternalApiClient
├── core/          # Domain models, services
└── infrastructure/# JPA entities, repositories, security, config
```

## API Endpoints

### Authentication
| Method | Endpoint | Access |
|--------|----------|--------|
| POST | `/api/auth/login` | Public |
| POST | `/api/auth/logout` | Auth |
| GET | `/api/auth/me` | Auth |
| PUT | `/api/auth/password` | Auth |

### Search & Browse
| Method | Endpoint | Access |
|--------|----------|--------|
| GET | `/api/search` | Public |
| GET | `/api/search/trending` | Public |
| GET | `/api/search/history` | Auth |
| POST | `/api/browsing-history/{itemId}` | Auth |
| GET | `/api/favorites` | Auth |
| POST | `/api/favorites/{itemId}` | Auth |
| DELETE | `/api/favorites/{itemId}` | Auth |

### Inventory
| Method | Endpoint | Access |
|--------|----------|--------|
| GET | `/api/inventory` | Auth |
| GET | `/api/inventory/{id}` | Auth |
| POST | `/api/inventory` | Admin |
| PUT | `/api/inventory/{id}` | Admin |
| DELETE | `/api/inventory/{id}` | Admin |

### Requests
| Method | Endpoint | Access |
|--------|----------|--------|
| POST | `/api/requests` | Student/Teacher |
| GET | `/api/requests/mine` | Auth |
| GET | `/api/requests/{id}` | Owner/Approver/Admin |
| GET | `/api/requests/pending-approval` | Teacher/Admin |
| PUT | `/api/requests/{id}/approve` | Teacher/Admin |
| PUT | `/api/requests/{id}/reject` | Teacher/Admin |
| PUT | `/api/requests/{id}/cancel` | Owner/Admin |
| PUT | `/api/requests/{id}/start-picking` | Admin |
| PUT | `/api/requests/{id}/ready-for-pickup` | Admin |
| PUT | `/api/requests/{id}/picked-up` | Admin |

### Notifications
| Method | Endpoint | Access |
|--------|----------|--------|
| GET | `/api/notifications` | Auth |
| GET | `/api/notifications/unread-count` | Auth |
| PUT | `/api/notifications/{id}/read` | Auth |
| PUT | `/api/notifications/read-all` | Auth |

### Profile & Preferences
| Method | Endpoint | Access |
|--------|----------|--------|
| GET | `/api/profile` | Auth |
| PUT | `/api/profile` | Auth |
| GET | `/api/profile/addresses` | Auth |
| POST | `/api/profile/addresses` | Auth |
| PUT | `/api/profile/addresses/{id}` | Auth |
| DELETE | `/api/profile/addresses/{id}` | Auth |
| GET | `/api/profile/tags` | Auth |
| POST | `/api/profile/tags` | Auth |
| DELETE | `/api/profile/tags/{tag}` | Auth |
| GET | `/api/profile/contacts` | Auth |
| POST | `/api/profile/contacts` | Auth |
| PUT | `/api/profile/contacts/{id}` | Auth |
| DELETE | `/api/profile/contacts/{id}` | Auth |
| GET | `/api/user/preferences` | Auth |
| PUT | `/api/user/preferences/dnd` | Auth |
| PUT | `/api/user/preferences/personalization` | Auth |
| GET | `/api/notification-preferences` | Auth |
| PUT | `/api/notification-preferences` | Auth |

### Admin
| Method | Endpoint | Access |
|--------|----------|--------|
| GET | `/api/admin/users` | Admin |
| POST | `/api/admin/users` | Admin |
| GET | `/api/admin/users/{id}` | Admin |
| PUT | `/api/admin/users/{id}` | Admin |
| PUT | `/api/admin/users/{id}/status` | Admin |
| PUT | `/api/admin/users/{id}/roles` | Admin |
| GET | `/api/admin/departments` | Admin |
| POST | `/api/admin/departments` | Admin |
| GET | `/api/admin/categories` | Admin |
| POST | `/api/admin/categories` | Admin |
| GET | `/api/admin/zones` | Admin |
| POST | `/api/admin/zones` | Admin |
| POST | `/api/admin/zones/distances` | Admin |
| GET | `/api/admin/policies` | Admin |
| PUT | `/api/admin/policies/{entityType}` | Admin |
| GET | `/api/admin/audit` | Admin |
| GET | `/api/admin/email-outbox` | Admin |
| POST | `/api/admin/email-outbox/export` | Admin |

### Warehouse
| Method | Endpoint | Access |
|--------|----------|--------|
| GET/POST | `/api/warehouse/locations` | Admin |
| PUT | `/api/warehouse/locations/{id}` | Admin |
| POST | `/api/warehouse/putaway` | Admin |
| POST | `/api/warehouse/pick-path` | Admin |
| POST | `/api/warehouse/simulate` | Admin |

### Crawler
| Method | Endpoint | Access |
|--------|----------|--------|
| GET/POST | `/api/admin/crawler/jobs` | Admin |
| PUT | `/api/admin/crawler/jobs/{id}` | Admin |
| POST | `/api/admin/crawler/jobs/{id}/run` | Admin |
| GET | `/api/admin/crawler/jobs/{id}/failures` | Admin |

## Configuration

All configuration is via environment variables (no .env files):

| Variable | Default | Description |
|----------|---------|-------------|
| `MASTER_KEY_PASSPHRASE` | **Required** | AES-256 master key (RAM only) |
| `SERVER_SSL_KEY_STORE_PASSWORD` | **Required** | TLS keystore password |
| `MYSQL_ROOT_PASSWORD` | campusstore_root | MySQL root password |
| `MYSQL_USER` | campusstore | App database user |
| `MYSQL_PASSWORD` | campusstore_pass | App database password |
| `SERVER_PORT` | 8443 | HTTPS port |
| `LOG_LEVEL` | INFO | Application log level |

## Security

- **Master Key**: Read once at startup, derived into AES-256 key via PBKDF2 (310,000 iterations), held only in volatile memory
- **PII Encryption**: Email, phone, all address fields, and password hashes encrypted with AES-256-GCM at rest
- **Phone Masking**: API responses show only last 4 digits unless caller has ADMIN_DATA_PRIVACY privilege
- **Rate Limiting**: Anonymous 30 req/min, authenticated 120 req/min, escalating lockouts persisted to DB
- **TLS**: All traffic over HTTPS with configurable self-signed certificate
- **Audit**: Immutable append-only audit trail with 7-year retention
- **CSRF**: CookieCsrfTokenRepository on all state-changing endpoints

## Data Retention

| Data | Retention |
|------|-----------|
| Search logs | 365 days |
| Browsing history | 365 days |
| Notifications | 365 days |
| Email outbox | 365 days |
| Audit logs | 7 years (immutable, never deleted) |
| Disabled users | Hard-deleted after 30 days (unless open dispute) |
