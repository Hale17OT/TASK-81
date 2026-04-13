# CampusStore Static Delivery Acceptance & Architecture Audit

## 1. Verdict
- **Overall conclusion: Partial Pass**

## 2. Scope and Static Verification Boundary
- Reviewed: `README.md`, `pom.xml`, `src/main/java/**`, `src/main/resources/**` (including Flyway SQL and templates), and `src/test/java/**`.
- Not reviewed: runtime behavior, container orchestration behavior, live browser rendering, and external/intranet connectivity behavior.
- Intentionally not executed: app startup, Docker, tests, scripts, migrations, browser/E2E flows.
- Manual verification required for: TLS redirect/termination behavior in deployed environment, actual Thymeleaf render behavior under live data, and real scheduler execution timing/operational reliability.

## 3. Repository / Requirement Mapping Summary
- Prompt core goal mapped: offline/on-prem school marketplace + inventory + governance with role-based workflow, search/filter/sort/personalization, profile/preferences, notifications/reminders, crawler observability, and warehouse strategy simulation.
- Main mapped implementation areas: Spring Security config (`SecurityConfig`), API/web controller split (`api/**` and `web/**`), core services (`RequestService`, `SearchService`, `NotificationService`, `CrawlerService`, `WarehouseService`, `AuditService`), persistence schema (`V1__initial_schema.sql`, `V2__seed_data.sql`), and tests.
- Major gap found against prompt: no admin-facing policy-management API/UI despite policies being part of required admin scope.

## 4. Section-by-section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and static verifiability
- **Conclusion: Partial Pass**
- **Rationale:** Startup/config docs are present and mostly consistent (Docker, env vars, API surface), but test execution documentation is statically inconsistent with the JUnit tag configuration.
- **Evidence:** `README.md:21`, `README.md:26`, `README.md:74`, `README.md:149`, `docker-compose.yml:24`, `pom.xml:155`, `pom.xml:161`, `run_tests.sh:11`, `run_tests.sh:16`, `run_tests.sh:47`, `src/test/java/com/campusstore/e2e/LoginE2ETest.java:18`
- **Manual verification note:** Confirm actual test-selection behavior of `mvn test` in CI after correcting tag/group config.

#### 4.1.2 Material deviation from Prompt
- **Conclusion: Partial Pass**
- **Rationale:** Core platform intent is implemented, but prompt-required "Administrators manage ... policies" is not represented as a management feature (no policy CRUD endpoints/pages).
- **Evidence:** `README.md:7`, `README.md:11`, `README.md:204`, `src/main/java/com/campusstore/web/controller/AdminWebController.java:38`, `src/main/java/com/campusstore/web/controller/AdminWebController.java:252`, `src/main/resources/templates/fragments/nav.html:49`, `src/main/java/com/campusstore/core/service/AuditService.java:124`

### 4.2 Delivery Completeness

#### 4.2.1 Core explicit requirements coverage
- **Conclusion: Partial Pass**
- **Rationale:** Most core flows exist (roles, search/filter/sort, requests/approvals, notifications, crawler metrics/alerts/snapshots, warehouse strategy); policy management is missing as an explicit admin capability.
- **Evidence:** `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:77`, `src/main/java/com/campusstore/core/service/SearchService.java:217`, `src/main/java/com/campusstore/core/service/SearchService.java:269`, `src/main/java/com/campusstore/core/service/RequestService.java:479`, `src/main/java/com/campusstore/core/service/NotificationService.java:67`, `src/main/java/com/campusstore/core/service/ScheduledJobService.java:87`, `src/main/java/com/campusstore/core/service/CrawlerService.java:242`, `src/main/java/com/campusstore/core/service/WarehouseService.java:275`, `src/main/java/com/campusstore/web/controller/AdminWebController.java:38`

#### 4.2.2 End-to-end deliverable completeness (0→1)
- **Conclusion: Pass**
- **Rationale:** Complete multi-module Spring Boot project with schema, seed data, API, web layer, config, and tests.
- **Evidence:** `pom.xml:28`, `src/main/java/com/campusstore/CampusStoreApplication.java:7`, `src/main/resources/db/migration/V1__initial_schema.sql:9`, `src/main/resources/db/migration/V2__seed_data.sql:11`, `README.md:85`

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Structure and decomposition
- **Conclusion: Pass**
- **Rationale:** Reasonable module decomposition (`api`, `web`, `core`, `infrastructure`) and service-level separation.
- **Evidence:** `README.md:114`, `src/main/java/com/campusstore/api/controller/RequestController.java:27`, `src/main/java/com/campusstore/web/client/InternalApiClient.java:35`, `src/main/java/com/campusstore/core/service/RequestService.java:34`, `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:24`

#### 4.3.2 Maintainability/extensibility
- **Conclusion: Partial Pass**
- **Rationale:** Core services are extensible, but there are functional coupling defects (warehouse view model mismatch) and missing policy-management boundary.
- **Evidence:** `src/main/java/com/campusstore/web/controller/AdminWebController.java:224`, `src/main/resources/templates/warehouse/index.html:18`, `src/main/java/com/campusstore/core/service/AuditService.java:124`

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error handling, logging, validation, API design
- **Conclusion: Partial Pass**
- **Rationale:** Exception handling, validation, and logging foundations are present; however, some key workflows have correctness risks (deletion workflow vs FK semantics, policy-management gap, test config mismatch).
- **Evidence:** `src/main/java/com/campusstore/infrastructure/config/GlobalExceptionHandler.java:27`, `src/main/java/com/campusstore/core/service/AuditService.java:173`, `src/main/resources/db/migration/V1__initial_schema.sql:506`, `src/main/java/com/campusstore/api/dto/AddAddressRequest.java:24`, `run_tests.sh:11`

#### 4.4.2 Product-like organization vs demo/sample
- **Conclusion: Pass**
- **Rationale:** Project resembles product architecture (security, persistence, scheduler jobs, operational logs, UI screens) rather than a single-sample demo.
- **Evidence:** `src/main/java/com/campusstore/core/service/ScheduledJobService.java:43`, `src/main/resources/logback-spring.xml:31`, `src/main/resources/templates/admin/audit.html:1`, `src/main/resources/templates/crawler/index.html:1`

### 4.5 Prompt Understanding and Requirement Fit

#### 4.5.1 Business-goal fit and constraint adherence
- **Conclusion: Partial Pass**
- **Rationale:** Business semantics are broadly understood (offline posture checks, role controls, search personalization toggle, crawler thresholds, warehouse strategy), but required admin policy-management capability is not delivered and user-deletion dispute logic conflicts with schema behavior.
- **Evidence:** `src/main/java/com/campusstore/core/service/CrawlerService.java:86`, `src/main/java/com/campusstore/core/service/SearchService.java:99`, `src/main/java/com/campusstore/core/service/RequestService.java:479`, `src/main/java/com/campusstore/core/service/AuditService.java:181`, `src/main/resources/db/migration/V1__initial_schema.sql:506`

### 4.6 Aesthetics (frontend/full-stack)

#### 4.6.1 Visual/interaction quality fit
- **Conclusion: Cannot Confirm Statistically**
- **Rationale:** Templates/CSS/JS are present and structured, but final rendering quality and interaction behavior require runtime browser validation.
- **Evidence:** `src/main/resources/templates/layout/main.html:1`, `src/main/resources/static/css/fluent-base.css:1`, `src/main/resources/static/css/fluent-layout.css:1`, `src/main/resources/static/js/app.js:1`
- **Manual verification note:** Validate mobile/desktop rendering and interaction feedback in-browser.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker / High

1) **Severity: High**  
**Title:** User hard-delete workflow contradicts dispute FK behavior  
**Conclusion:** Fail  
**Evidence:** `src/main/java/com/campusstore/core/service/AuditService.java:181`, `src/main/java/com/campusstore/core/service/AuditService.java:218`, `src/main/resources/db/migration/V1__initial_schema.sql:494`, `src/main/resources/db/migration/V1__initial_schema.sql:506`  
**Impact:** Prompt requires deletion after 30 days unless **open** dispute; current schema likely prevents deleting users with any dispute row (resolved/dismissed included), causing silent retention drift and non-compliance.  
**Minimum actionable fix:** Either (a) change dispute FK delete behavior and preserve dispute history via placeholder mapping, or (b) migrate disputes to anonymized placeholder before user delete; enforce logic aligned to open-dispute-only exemption.

2) **Severity: High**  
**Title:** Missing admin policy-management capability required by prompt  
**Conclusion:** Fail  
**Evidence:** `README.md:7`, `README.md:204`, `src/main/java/com/campusstore/web/controller/AdminWebController.java:38`, `src/main/resources/templates/fragments/nav.html:49`, `src/main/java/com/campusstore/core/service/AuditService.java:124`  
**Impact:** Explicit business requirement "Administrators manage ... policies" is not implemented as a user-manageable feature (no policy API/UI), reducing governance completeness.  
**Minimum actionable fix:** Add admin policy endpoints + Thymeleaf pages to list/update retention and governance policies with audit logging.

3) **Severity: High**  
**Title:** Warehouse admin page likely binds paged model incorrectly  
**Conclusion:** Fail  
**Evidence:** `src/main/java/com/campusstore/web/controller/AdminWebController.java:224`, `src/main/resources/templates/warehouse/index.html:18`  
**Impact:** Location table iteration uses `${locations}` while controller provides a paged wrapper; this likely breaks location rendering on a core admin workflow page.  
**Minimum actionable fix:** Iterate `locations.content` (and pagination metadata accordingly), aligned with other templates that already use `.content`.

### Medium

4) **Severity: Medium**  
**Title:** Test phase selection is misconfigured for JUnit 5 tags  
**Conclusion:** Partial Fail  
**Evidence:** `pom.xml:161`, `run_tests.sh:11`, `run_tests.sh:16`, `run_tests.sh:47`, `src/test/java/com/campusstore/e2e/LoginE2ETest.java:18`  
**Impact:** Documented unit/integration/e2e split is not statically reliable; `groups`/`excludedGroups` config does not match JUnit `@Tag` semantics, risking wrong suites running/skipping.  
**Minimum actionable fix:** Use JUnit tag selectors (`-DincludeTags`/`-DexcludeTags` or surefire `groups` only with TestNG). Update `run_tests.sh` + Maven config consistently.

5) **Severity: Medium**  
**Title:** DND clear flow not represented in UI request payload semantics  
**Conclusion:** Partial Fail  
**Evidence:** `src/main/java/com/campusstore/api/dto/DndRequest.java:10`, `src/main/resources/templates/profile/notification-settings.html:53`  
**Impact:** Backend supports clearing DND with both values null; UI posts raw input values (likely empty strings), making clear/reset behavior ambiguous and potentially failing validation.  
**Minimum actionable fix:** Normalize empty time inputs to `null` in frontend payload (or backend binder) and add explicit "Clear DND" action.

## 6. Security Review Summary

- **Authentication entry points: Pass** — Form login and API auth endpoints exist with session-based auth; non-authenticated access to protected APIs is denied in security config. Evidence: `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:77`, `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:123`, `src/main/java/com/campusstore/api/controller/AuthController.java:52`.
- **Route-level authorization: Pass** — Admin/teacher/warehouse routes are constrained via matcher rules and `@PreAuthorize`. Evidence: `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:80`, `src/main/java/com/campusstore/api/controller/WarehouseController.java:32`, `src/main/java/com/campusstore/api/controller/UserAdminController.java:34`.
- **Object-level authorization: Partial Pass** — Strong checks exist for request detail/contact/address/notification ownership, but coverage is not uniform for every domain object and several API tests are mocked. Evidence: `src/main/java/com/campusstore/core/service/RequestService.java:479`, `src/main/java/com/campusstore/core/service/ProfileService.java:109`, `src/main/java/com/campusstore/core/service/NotificationService.java:129`.
- **Function-level authorization: Pass** — Sensitive transitions include role checks and service-side guards. Evidence: `src/main/java/com/campusstore/core/service/RequestService.java:189`, `src/main/java/com/campusstore/core/service/RequestService.java:327`.
- **Tenant/user data isolation: Partial Pass** — Request and profile ownership checks are present; no multi-tenant concept beyond user ownership is modeled. Evidence: `src/main/java/com/campusstore/core/service/RequestService.java:503`, `src/main/java/com/campusstore/core/service/ProfileService.java:176`, `src/main/java/com/campusstore/core/service/ProfileService.java:199`.
- **Admin/internal/debug protection: Pass** — `/api/admin/**` and web `/admin/**` are admin-only; health is intentionally public. Evidence: `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:80`, `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:120`.

## 7. Tests and Logging Review

- **Unit tests: Pass** — Unit coverage exists for rate limiting, encryption, request authorization, warehouse algorithms, and DND logic. Evidence: `src/test/java/com/campusstore/unit/infrastructure/RateLimitFilterTest.java:24`, `src/test/java/com/campusstore/unit/service/RequestServiceAuthorizationTest.java:35`, `src/test/java/com/campusstore/unit/service/NotificationDndTest.java:36`.
- **API/integration tests: Partial Pass** — Many API tests use `@MockitoBean` for core services, limiting true end-to-end confidence; some non-mocked persistence tests are present. Evidence: `src/test/java/com/campusstore/integration/api/AdminApiTest.java:58`, `src/test/java/com/campusstore/integration/api/RequestApiTest.java:58`, `src/test/java/com/campusstore/integration/persistence/AuthorizationAndGatePersistenceTest.java:60`.
- **Logging categories/observability: Pass** — Structured app + audit log appenders are configured; audit logger separated. Evidence: `src/main/resources/logback-spring.xml:31`, `src/main/resources/logback-spring.xml:46`, `src/main/java/com/campusstore/core/service/AuditService.java:46`.
- **Sensitive-data leakage risk in logs/responses: Partial Pass** — Password/email/phone fields are encrypted/ignored and audit scrubber exists, but persistent repository log artifacts are committed in `logs/` and should not be shipped as delivery artifacts. Evidence: `src/main/java/com/campusstore/infrastructure/persistence/entity/UserEntity.java:35`, `src/main/java/com/campusstore/core/service/AuditService.java:241`, `logs/audit.log:1`.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit and integration test classes exist under `src/test/java`; E2E Playwright tests also exist.
- Frameworks: JUnit 5 + Spring Boot Test + Mockito + MockMvc + Playwright.
- Test entry points are documented via `run_tests.sh`, but tag/group selection is misaligned.
- Evidence: `pom.xml:100`, `pom.xml:155`, `run_tests.sh:11`, `run_tests.sh:47`, `src/test/java/com/campusstore/e2e/LoginE2ETest.java:18`.

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Unauthenticated 401 / protected route guards | `src/test/java/com/campusstore/integration/security/SecurityConfigTest.java:102` | `status().isUnauthorized()` and role-based 403 checks | basically covered | Mostly mocked services | Add non-mocked security integration over real service stack for key endpoints |
| Search public access + personalization toggle behavior | `src/test/java/com/campusstore/integration/api/SearchApiTest.java:98` | `verify(searchService).search(... eq(true/false) ...)` | basically covered | Does not verify DB-backed ranking/history behavior | Add non-mocked search integration test with real H2 seed data and ordering assertions |
| Object-level request isolation | `src/test/java/com/campusstore/unit/service/RequestServiceAuthorizationTest.java:72`, `src/test/java/com/campusstore/integration/persistence/AuthorizationAndGatePersistenceTest.java:81` | `assertThrows(AccessDeniedException.class)` for unauthorized viewers/approvers | sufficient | Endpoint-level detail API not deeply non-mocked | Add MockMvc + real DB test for `/api/requests/{id}` cross-user denial |
| Notification ownership isolation | `src/test/java/com/campusstore/integration/persistence/CrossUserIsolationPersistenceTest.java:60` | attacker cannot mark owner notification read | sufficient | No API-level cross-user read/write negative tests | Add API test for notification read endpoint with mismatched user |
| Rate limiting and lockout escalation | `src/test/java/com/campusstore/unit/infrastructure/RateLimitFilterTest.java:126` | 30/120 limits and 429 lockout behavior | basically covered | No integration-level test with real filter chain + persistence row behavior | Add Spring integration test validating DB lockout persistence across requests |
| DND + critical override | `src/test/java/com/campusstore/unit/service/NotificationDndTest.java:84` | critical still delivered when opted out / during DND | basically covered | No API/UI flow test for clearing DND | Add API test for null-clear semantics and UI payload normalization |
| Warehouse simulation schema and admin protection | `src/test/java/com/campusstore/unit/service/WarehouseSimulationSchemaTest.java:1`, `src/test/java/com/campusstore/integration/api/AdminApiTest.java:282` | schema keys asserted under `$.data.*`, teacher 403 on simulate | basically covered | Real algorithm perf/quality not validated end-to-end | Add non-mocked historical-order simulation regression test |
| Crawler threshold alerting/snapshot behavior | (No strong direct integration test found) | N/A | insufficient | Core prompt-critical failure snapshot/threshold flow not proven by tests | Add service integration tests for threshold breach triggering alert + snapshot cap |
| User deletion unless open dispute | `src/test/java/com/campusstore/unit/service/CryptographicErasureTest.java:1` | unit assertions only | insufficient | Does not cover FK interaction with non-open disputes | Add persistence integration test for deletion with RESOLVED dispute row |

### 8.3 Security Coverage Audit
- **Authentication:** basically covered by integration tests for login page, protected redirects, and API 401 behavior (`src/test/java/com/campusstore/integration/security/AuthenticationTest.java:109`, `src/test/java/com/campusstore/integration/security/SecurityConfigTest.java:103`).
- **Route authorization:** basically covered via role-based MockMvc tests (`src/test/java/com/campusstore/integration/api/AdminApiTest.java:105`, `src/test/java/com/campusstore/integration/api/RequestApiTest.java:247`).
- **Object-level authorization:** partially covered with meaningful service/persistence tests (`src/test/java/com/campusstore/unit/service/RequestServiceAuthorizationTest.java:72`, `src/test/java/com/campusstore/integration/persistence/CrossUserIsolationPersistenceTest.java:99`).
- **Tenant/data isolation:** partially covered (owner checks tested for selected entities only); severe defects could remain in untested resources.
- **Admin/internal protection:** basically covered for `/api/admin/**` and `/api/warehouse/**` (`src/test/java/com/campusstore/integration/api/AdminApiTest.java:219`).

### 8.4 Final Coverage Judgment
- **Partial Pass**
- Major security/authorization paths are tested to a useful baseline, but heavy Mockito usage in many "integration" tests and missing non-mocked coverage for crawler thresholding, deletion/dispute FK semantics, and some endpoint-level object isolation means severe defects can still slip through while tests pass.

## 9. Final Notes
- The project is substantial and aligned with most of the prompt, but high-severity functional gaps remain around policy-management completeness, user-deletion/dispute semantics, and a likely warehouse UI binding defect.
- Several conclusions (UI rendering correctness and operational scheduler behavior) remain **Manual Verification Required** due strict static-only audit boundaries.
