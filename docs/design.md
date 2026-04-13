# CampusStore Design

## 1. Purpose
CampusStore is an offline, on-prem marketplace and stockroom operations platform for school communities. It supports student requests, teacher approvals, and administrator governance across catalog, warehouse, crawler observability, and audit/compliance workflows.

## 2. System Context
- Runtime: Spring Boot monolith with split adapters (`api` + `web`)
- UI: Thymeleaf templates served by `web` controllers
- API: REST-style JSON endpoints under `/api/**`
- Data: MySQL (Flyway migrations)
- Security: Spring Security session auth + CSRF + role checks + service-layer authorization
- Deployment target: local network / on-prem only, TLS enforced

## 3. Architecture Style
Hexagonal-inspired layering:

- **Inbound adapters**
  - `com.campusstore.api.controller`: API endpoints
  - `com.campusstore.web.controller`: Thymeleaf page endpoints
- **Core**
  - `com.campusstore.core.service`: business workflows
  - `com.campusstore.core.domain`: enums/events/models
- **Outbound adapters**
  - `com.campusstore.infrastructure.persistence`: JPA entities/repositories
  - `com.campusstore.infrastructure.security`: auth, encryption, rate limiting
  - `com.campusstore.web.client`: web->API loopback client

## 4. Key Design Decisions

### 4.1 Web/API separation (same process)
Web controllers do not directly call domain services. They call internal API endpoints through an HTTP loopback client to preserve a consistent access boundary and request semantics.

### 4.2 Role and ownership governance
- Route-level constraints in Spring Security
- Method-level constraints (`@PreAuthorize`) on privileged API areas
- Object-level checks in services (for request ownership, assigned approvers, notification ownership, profile ownership)

### 4.3 Privacy and cryptographic controls
- Passwords: BCrypt hashes
- Sensitive profile/contact/address fields: AES-256-GCM encrypted at rest
- Phone masking in UI listings (last 4 digits)
- User deletion: 30-day eligible hard-delete, blocked by open dispute; closed disputes anonymized using placeholder linkage

### 4.4 Search and personalization
- Keyword search with tokenized matching
- Filters: category, price range, item condition, zone
- Sorts: newest, price, popularity, distance (zone distance table)
- Personalization uses recent browsing/favorites and can be toggled off

### 4.5 Operational safeguards
- HTTPS required in normal operation
- Rate limiting: anonymous and authenticated buckets + escalating lockouts
- Immutable audit logging for state changes
- Scheduled retention cleanup + deletion workflows

## 5. Core Domain Modules

- **Auth / Identity**: local username/password, session lifecycle, forced password rotation
- **Inventory & Catalog**: items, category, location assignment, active/inactive lifecycle
- **Request Workflow**: request creation, teacher/admin approval, picking lifecycle, pickup completion
- **Profile**: addresses, contacts, tags, personalization and DND preferences
- **Notification Center**: in-app notifications, notification preferences, email outbox export
- **Crawler Observability**: job config, run tracking, threshold alerts, failed sample snapshots
- **Warehouse Strategy**: putaway recommendation, path-cost matrix pick paths, simulation reporting
- **Governance**: audit trail, data retention policies, user erasure flow

## 6. Persistence Overview
Main persistent entities include:
- users, roles, departments
- inventory items, categories, storage locations, zones, zone distances
- item requests and pick tasks
- browsing history, search logs, favorites
- user preferences, notification preferences, notifications
- contacts and addresses (PII encrypted)
- crawler jobs/runs/failures
- audit logs and retention policies
- deleted user placeholders and disputes

Schema lifecycle is managed by Flyway migrations in `src/main/resources/db/migration`.

## 7. Security Model
- Authentication: form/session with local credentials
- Authorization: role-based + object-level checks in services
- CSRF enabled for state-changing endpoints
- TLS expected for all traffic in deployed mode
- Rate-limit and lockout controls at filter layer

## 8. Non-Goals / Boundaries
- No external SaaS dependencies required for core functionality
- No cloud map routing (distance is internal zone matrix)
- No direct outbound email sending; outbox export is offline-first

## 9. Traceability to Prompt Requirements
- Offline/on-prem operations: local DB + local services + outbox export
- Role-specific UX: Thymeleaf nav and controller gating
- Governance: audit logs, retention policy management, and erasure workflow
- Warehouse optimization: ABC/FIFO + pick path + simulation
- Crawler reliability: threshold monitoring and failure snapshots
