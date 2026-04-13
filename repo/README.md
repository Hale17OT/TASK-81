# CampusStore — Inventory & Marketplace Operations Platform

An offline, on-premises marketplace and stockroom workflow platform for school communities with strong governance, role-based access, and intelligent warehouse operations.

## Overview

CampusStore enables a school community to run a managed inventory marketplace entirely on local infrastructure — no internet required. Students browse and request items, Teachers act as department-scoped approvers, and Administrators manage the full catalog, warehouse operations, policies, and audits.

### Key Features

- **Role-based access**: Students, Teachers, Administrators with least-privilege enforcement
- **Approval workflow**: Configurable per-item governance with department-scoped teacher approval and self-approval blocking
- **Intelligent search**: FULLTEXT tokenized matching, multi-dimensional filters, zone-distance sorting, personalization with privacy toggle
- **Warehouse operations**: ABC classification, FIFO/expiration, 3D pick paths, putaway recommendations, shadow simulation engine
- **Notification center**: In-app notifications, email outbox for offline delivery, DND windows, per-category preferences
- **Crawler observability**: Local data source ingestion with threshold alerting and failure snapshots
- **Strong security**: AES-256 PII encryption, BCrypt passwords, forced TLS, rate limiting with escalating lockouts
- **Immutable audit trail**: 7-year retention with cryptographic erasure for deleted users
- **100% offline**: No external service dependencies

## Prerequisites

- Docker & Docker Compose (v2+)
- (Optional for local development) Java 17+, Maven 3.9+

## Quick Start

```bash
# Both MASTER_KEY_PASSPHRASE and SERVER_SSL_KEY_STORE_PASSWORD are required
MASTER_KEY_PASSPHRASE=YourSecurePassphrase2026 SERVER_SSL_KEY_STORE_PASSWORD=YourKeystorePassword docker compose up --build
```

The application will be available at **https://localhost:8443**

> The TLS certificate is self-signed. Your browser will show a security warning — proceed to accept it.

## Default Users (bootstrap-only)

These accounts exist only so a fresh deploy can be administered at all. They all ship
with `password_change_required=TRUE`, which means **the first login for each account
will force a password rotation at `/account/change-password` before any other page or
API call will succeed** (see `V3__force_password_rotation.sql`,
`ForcePasswordChangeInterceptor`).

| Username | Role | Department |
|----------|------|------------|
| admin | Administrator | — |
| teacher1 | Teacher | Science |
| teacher2 | Teacher | Engineering |
| student1 | Student | — |
| student2 | Student | — |

The bootstrap passwords for these accounts are intentionally not checked into this
repository. Pick the path that fits your situation:

- **Reviewer / local acceptance** — start the app with
  `SPRING_PROFILES_ACTIVE=dev CAMPUSSTORE_GENERATE_BOOTSTRAP_PASSWORD=true`.
  `SeedDataInitializer` generates a random 20-char `admin` password and writes it
  **only** to `bootstrap-admin-password.txt` in the working directory (owner-read-only
  on POSIX). The persistent log file receives only a `[REDACTED]` marker and nothing
  is written to stdout. The generator refuses to run outside the `dev` / `local` /
  `test` profiles, so production logs never touch plaintext credentials. Read the file,
  log in once with `admin` + that password, rotate at `/account/change-password`, then
  delete the file.
- **Production / controlled deploy** — start the app with `ADMIN_INITIAL_PASSWORD`
  set to a chosen value. The admin account adopts that password; rotation is still
  forced on first login.

Both paths are opt-in: without either env var, the seed BCrypt hashes stay in place
and the deployment runbook (delivered out-of-band) provides the matching passwords.
After any successful rotation the force-rotation flag clears and normal operation
resumes.

## Testing

```bash
./run_tests.sh
```

This unified script runs:
1. **Unit tests** — Domain logic, algorithms, validators (142 tests)
2. **Integration tests** — API endpoints, persistence, security with Spring Boot + H2 (80 tests)
3. **E2E tests** — Browser flows with Playwright against Docker environment (requires Docker running)

## Architecture

### Hexagonal (Ports & Adapters)

```
┌──────────────────────────────────────────────────┐
│                  Inbound Adapters                │
│  ┌──────────────┐    ┌───────────────────────┐   │
│  │ REST API      │    │ Thymeleaf Web         │   │
│  │ Controllers   │    │ Controllers           │   │
│  └──────┬───────┘    └──────────┬────────────┘   │
├─────────┼───────────────────────┼────────────────┤
│         │     Core Domain       │                │
│  ┌──────▼───────────────────────▼──────────┐     │
│  │  Use Case Interfaces (Ports)            │     │
│  │  Domain Services (Business Logic)       │     │
│  │  Domain Models & Enums                  │     │
│  └──────┬──────────────────────────────────┘     │
├─────────┼────────────────────────────────────────┤
│         │     Outbound Adapters                  │
│  ┌──────▼──────┐  ┌──────────┐  ┌────────────┐  │
│  │ JPA/MySQL   │  │ AES-256  │  │ Bucket4j   │  │
│  │ Persistence │  │ Encrypt  │  │ Rate Limit │  │
│  └─────────────┘  └──────────┘  └────────────┘  │
└──────────────────────────────────────────────────┘
```

### Module Structure

```
src/main/java/com/campusstore/
├── api/           # REST controllers, DTOs, validators
├── web/           # Thymeleaf page controllers
├── core/          # Domain models, use case interfaces, services
└── infrastructure/# JPA entities, repositories, security, config
```

### Web ↔ API Boundary (HTTP loopback)

The Thymeleaf controllers under `com/campusstore/web/**` never talk to domain services
directly. They go through `web/client/InternalApiClient`, which issues **real HTTP calls**
to the application's own `/api/**` surface over loopback TLS.

- **Transport**: `RestClient` built with a trust-all SSL context against
  `https://localhost:${server.port}` (self-signed cert, loopback only).
  See `web/client/RestClientConfig`.
- **Auth forwarding**: an outbound interceptor copies the inbound request's `JSESSIONID`
  cookie so the API call authenticates as the same user. For state-changing methods
  (POST/PUT/DELETE/PATCH) it also forwards the `XSRF-TOKEN` cookie as the
  `X-XSRF-TOKEN` header.
- **Rate-limit handling**: every loopback call carries a per-process `X-Internal-Loopback`
  secret (generated at startup). `RateLimitFilter` recognises this header and skips its
  bucket, so a page render that fans out to several API endpoints does not drain the
  authenticated user's `120/min` budget.
- **Owner-scoped PII over TLS**: the three profile GETs (`/api/profile`,
  `/api/profile/addresses`, `/api/profile/contacts`) return decrypted DTOs
  (`ProfileResponse`, `AddressResponse`, `ContactResponse`) scoped to the authenticated
  owner. Decryption happens server-side inside the API layer; plaintext PII leaves the
  process only over loopback TLS.

API integration tests exercise `/api/**` end-to-end over HTTP; web integration tests that
use `MockMvc` (no real servlet listening) mock `InternalApiClient` directly since an HTTP
loopback is not available in that environment.

## API Documentation

### Authentication
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/auth/login` | Public | Login |
| POST | `/api/auth/logout` | Auth | Logout |
| GET | `/api/auth/me` | Auth | Current user |
| PUT | `/api/auth/password` | Auth | Change password |

### Inventory
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/inventory` | Auth | List items |
| GET | `/api/inventory/{id}` | Auth | Item detail |
| POST | `/api/inventory` | Admin | Create item |
| PUT | `/api/inventory/{id}` | Admin | Update item |
| DELETE | `/api/inventory/{id}` | Admin | Deactivate |

### Search
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/search?q=&categoryId=&priceMin=&priceMax=&condition=&zoneId=&sort=&page=&size=` | Public | Search items |
| GET | `/api/search/trending` | Public | Trending terms |
| GET | `/api/search/history` | Auth | Search history |

### Requests
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/requests` | Student/Teacher | Create request |
| GET | `/api/requests/mine` | Auth | My requests |
| GET | `/api/requests/pending-approval` | Teacher/Admin | Pending approvals |
| GET | `/api/requests/{id}` | Auth | Request detail |
| PUT | `/api/requests/{id}/approve` | Teacher/Admin | Approve |
| PUT | `/api/requests/{id}/reject` | Teacher/Admin | Reject |
| PUT | `/api/requests/{id}/cancel` | Owner/Admin | Cancel |
| PUT | `/api/requests/{id}/start-picking` | Admin | Staff begins picking (APPROVED → PICKING) |
| PUT | `/api/requests/{id}/ready-for-pickup` | Admin | Items staged (PICKING → READY_FOR_PICKUP) |
| PUT | `/api/requests/{id}/picked-up` | Admin | Mark picked up |

### Notifications
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/notifications` | Auth | List notifications |
| GET | `/api/notifications/unread-count` | Auth | Unread count |
| PUT | `/api/notifications/{id}/read` | Auth | Mark read |
| PUT | `/api/notifications/read-all` | Auth | Mark all read |

### Categories
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/categories` | Auth | Public category list for search/filter UIs |
| GET | `/api/admin/categories` | Admin | Legacy admin-scoped list (prefer `/api/categories`) |
| POST | `/api/admin/categories` | Admin | Create category |

### Admin
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET/POST | `/api/admin/users` | Admin | User management |
| GET | `/api/admin/users/{id}` | Admin | Get user detail |
| PUT | `/api/admin/users/{id}` | Admin | Update user |
| PUT | `/api/admin/users/{id}/status` | Admin | Change user status |
| PUT | `/api/admin/users/{id}/roles` | Admin | Update user roles |
| GET/POST | `/api/admin/departments` | Admin | Departments |
| GET/POST | `/api/admin/categories` | Admin | Categories (admin-only; public list at `/api/categories`) |
| GET | `/api/admin/audit` | Admin | Audit log |
| GET | `/api/admin/policies` | Admin | List data-retention / governance policies |
| PUT | `/api/admin/policies/{entityType}` | Admin | Update retention policy (audit-logged as `POLICY_UPDATE`) |
| GET | `/api/admin/email-outbox` | Admin | List outbox messages |
| POST | `/api/admin/email-outbox/export` | Admin | Export outbox ZIP |
| GET/POST | `/api/admin/crawler/jobs` | Admin | Crawler management |
| PUT | `/api/admin/crawler/jobs/{id}` | Admin | Update crawler job |
| POST | `/api/admin/crawler/jobs/{id}/run` | Admin | Run crawler job |
| GET | `/api/admin/crawler/jobs/{id}/failures` | Admin | View job failures |

### Warehouse
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET/POST | `/api/warehouse/locations` | Admin | Location management |
| PUT | `/api/warehouse/locations/{id}` | Admin | Update location |
| POST | `/api/warehouse/putaway` | Admin | Putaway recommendation |
| POST | `/api/warehouse/pick-path` | Admin | Generate pick path |
| POST | `/api/warehouse/simulate` | Admin | Shadow simulation |

### Profile
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/profile` | Auth | Get profile |
| PUT | `/api/profile` | Auth | Update profile |
| GET | `/api/profile/addresses` | Auth | List addresses |
| POST | `/api/profile/addresses` | Auth | Add address |
| PUT | `/api/profile/addresses/{id}` | Auth | Update address |
| DELETE | `/api/profile/addresses/{id}` | Auth | Delete address |
| GET | `/api/profile/tags` | Auth | List interest tags |
| POST | `/api/profile/tags` | Auth | Add interest tag |
| DELETE | `/api/profile/tags/{tag}` | Auth | Remove interest tag |
| GET | `/api/profile/contacts` | Auth | List contacts (name/email/phone/notes encrypted at rest) |
| POST | `/api/profile/contacts` | Auth | Add contact |
| PUT | `/api/profile/contacts/{id}` | Auth | Update contact |
| DELETE | `/api/profile/contacts/{id}` | Auth | Delete contact |

### User Preferences
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/user/preferences` | Auth | Get preferences |
| PUT | `/api/user/preferences/dnd` | Auth | Update DND window |
| PUT | `/api/user/preferences/personalization` | Auth | Toggle personalization |

### Notification Preferences
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/notification-preferences` | Auth | Get notification prefs |
| PUT | `/api/notification-preferences` | Auth | Update notification prefs |

### Browsing & Favorites
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/browsing-history/{itemId}` | Auth | Record browse event |
| GET | `/api/favorites` | Auth | List favorites |
| POST | `/api/favorites/{itemId}` | Auth | Add favorite |
| DELETE | `/api/favorites/{itemId}` | Auth | Remove favorite |

### Zones (Admin)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/admin/zones` | Admin | List zones |
| POST | `/api/admin/zones` | Admin | Create zone |
| POST | `/api/admin/zones/distances` | Admin | Set zone distance |

## Configuration

All configuration is via environment variables (no .env files):

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_ROOT_PASSWORD` | campusstore_root | MySQL root password |
| `MYSQL_USER` | campusstore | App database user |
| `MYSQL_PASSWORD` | campusstore_pass | App database password |
| `MASTER_KEY_PASSPHRASE` | **Required** | AES-256 master key (RAM only) |
| `SERVER_SSL_KEY_STORE_PASSWORD` | **Required** | TLS keystore password (must match generate-keystore.sh) |
| `SERVER_PORT` | 8443 | HTTPS port |
| `LOG_LEVEL` | INFO | Application log level |

## Security Notes

- **Master Key**: The `MASTER_KEY_PASSPHRASE` is read once at startup, derived into an AES-256 key via PBKDF2 (310,000 iterations), and held only in volatile memory. It is never written to disk.
- **PII Encryption**: Email, phone, and address fields are encrypted with AES-256-GCM at rest.
- **Phone Masking**: UI listings display only the last 4 digits (e.g., `***-***-1234`). Full decrypted phone numbers are returned by `/api/profile` and `/api/profile/contacts` only to the authenticated owner for use in edit forms, transported over loopback TLS. ADMIN_DATA_PRIVACY-privileged callers may obtain unmasked values via dedicated admin endpoints; all other callers receive the masked form only.
- **Rate Limiting**: Anonymous 30 req/min, authenticated 120 req/min. Escalating lockouts after violations.
- **TLS**: All traffic forced over HTTPS with self-signed certificate.
- **Audit**: All state-changing operations logged to immutable audit trail (7-year retention).
- **Cryptographic Erasure**: Deleted users' PII is overwritten with SHA-256 hashes in audit logs.

## Data Retention

| Data | Retention |
|------|-----------|
| Search logs | 365 days |
| Browsing history | 365 days |
| Notifications | 365 days |
| Email outbox | 365 days |
| Audit logs | 7 years (2,555 days) |
| Disabled users | Hard-deleted after 30 days (unless open dispute) |

## Offline Operations

- **Email**: Messages queued in `email_outbox` table. Admin exports to ZIP for manual transfer.
- **Crawler**: Ingests local files and intranet pages. Failed samples stored for offline debugging.
- **No CDN**: All CSS, JS, and assets served locally.
- **No external APIs**: Zone distance uses internal adjacency matrix, not maps.
