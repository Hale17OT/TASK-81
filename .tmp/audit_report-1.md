# CampusStore Static Audit Report

## 1. Verdict
- **Overall conclusion: Partial Pass**

## 2. Scope and Static Verification Boundary
- **Reviewed:** project docs/config, API + web entry points, security config/filtering, core services (search/request/profile/notification/crawler/warehouse/audit), schema migrations, representative Thymeleaf templates, and test suites.
- **Not reviewed exhaustively:** every single template/style variant and every minor helper class.
- **Intentionally not executed:** app startup, tests, Docker, browser/E2E flows, external/intranet crawling, scheduled jobs.
- **Manual verification required:** runtime TLS wiring/certs, true end-to-end REST consumption by frontend over HTTP, scheduler behavior over time windows, crawler network behavior, and UI rendering/usability in browser.

## 3. Repository / Requirement Mapping Summary
- **Prompt core goal mapped:** offline campus marketplace + inventory governance with role-based UX, search/filter/sort/personalization, request approvals, notifications/DND, crawler observability, warehouse strategy simulation, encryption, TLS, rate limiting, retention/audit.
- **Main implementation areas mapped:** Spring Security + method guards, API controllers under `/api/**`, Thymeleaf controllers/templates, services in `core/service`, JPA schema/migrations, scheduled jobs, and tests under `src/test`.
- **Key deviations found:** frontend does not actually consume REST over HTTP (internal service adapter), missing Thymeleaf contact-management area, and documentation/code mismatch for production bootstrap-password path.

## 4. Section-by-section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and static verifiability
- **Conclusion: Partial Pass**
- **Rationale:** README provides startup/testing/API/config docs, but there is a material docs-to-code contradiction for bootstrap password flow in production.
- **Evidence:** `README.md:65`, `README.md:77`, `README.md:122`, `README.md:249`, `src/main/java/com/campusstore/infrastructure/config/SeedDataInitializer.java:96`
- **Manual verification note:** verify whether deployment runbook resolves the contradiction.

#### 4.1.2 Material deviation from Prompt
- **Conclusion: Fail**
- **Rationale:** Prompt requires decoupled REST API consumed by Thymeleaf frontend; implementation uses in-process service delegation via `InternalApiClient` instead of actual REST calls.
- **Evidence:** `src/main/java/com/campusstore/web/client/InternalApiClient.java:24`, `src/main/java/com/campusstore/web/client/InternalApiClient.java:73`, `src/main/java/com/campusstore/web/controller/SearchController.java:63`, `src/main/java/com/campusstore/web/controller/ProfileWebController.java:45`

### 4.2 Delivery Completeness

#### 4.2.1 Core explicit requirements coverage
- **Conclusion: Partial Pass**
- **Rationale:** most major capabilities exist (roles/search/filters/sort/distance, approvals, notifications+DND, crawler metrics/alerts/snapshots, warehouse simulation, encryption, TLS, rate limiting, retention). However, profile contact management is missing in Thymeleaf UX despite explicit profile-area requirement.
- **Evidence:** `src/main/resources/templates/profile/index.html:80`, `src/main/resources/templates/profile/addresses.html:1`, `src/main/resources/templates/profile/notification-settings.html:1`, `src/main/java/com/campusstore/api/controller/ProfileController.java:136`, `src/main/resources/templates/fragments/nav.html:90`

#### 4.2.2 End-to-end 0→1 deliverable completeness
- **Conclusion: Pass**
- **Rationale:** repository has full multi-module structure, migrations, API/web layers, configs, and tests; not a fragment/demo.
- **Evidence:** `pom.xml:28`, `src/main/resources/db/migration/V1__initial_schema.sql:9`, `src/main/java/com/campusstore/CampusStoreApplication.java:1`, `README.md:85`

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Structure and module decomposition
- **Conclusion: Pass**
- **Rationale:** clear separation across `api`, `web`, `core`, `infrastructure`; services and repositories are modular.
- **Evidence:** `README.md:114`, `src/main/java/com/campusstore/api/controller/RequestController.java:27`, `src/main/java/com/campusstore/core/service/RequestService.java:34`, `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:24`

#### 4.3.2 Maintainability/extensibility
- **Conclusion: Partial Pass**
- **Rationale:** core logic is mostly extendable, but direct entity exposure and in-process web adapter reduce API-boundary integrity and future decoupling.
- **Evidence:** `src/main/java/com/campusstore/api/controller/ProfileController.java:41`, `src/main/java/com/campusstore/api/controller/RequestController.java:77`, `src/main/java/com/campusstore/web/client/InternalApiClient.java:24`

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error handling/logging/validation/API design
- **Conclusion: Partial Pass**
- **Rationale:** centralized exception handling and structured responses exist, but some request models (e.g., DND) lack validation and some endpoints return JPA entities directly.
- **Evidence:** `src/main/java/com/campusstore/infrastructure/config/GlobalExceptionHandler.java:27`, `src/main/java/com/campusstore/api/dto/DndRequest.java:5`, `src/main/java/com/campusstore/api/controller/NotificationController.java:30`

#### 4.4.2 Product-like deliverable vs demo
- **Conclusion: Pass**
- **Rationale:** includes governance/security/retention/crawler/warehouse modules and non-trivial UX templates.
- **Evidence:** `src/main/java/com/campusstore/core/service/ScheduledJobService.java:43`, `src/main/java/com/campusstore/core/service/CrawlerService.java:242`, `src/main/resources/templates/warehouse/index.html:59`

### 4.5 Prompt Understanding and Requirement Fit
- **Conclusion: Partial Pass**
- **Rationale:** business domain is well understood overall, but requirement semantics around "REST consumed by Thymeleaf" and full profile contacts UX are not fully met.
- **Evidence:** `src/main/java/com/campusstore/web/client/InternalApiClient.java:20`, `src/main/resources/templates/profile/index.html:80`, `src/main/java/com/campusstore/api/controller/ProfileController.java:136`

### 4.6 Aesthetics (frontend)
- **Conclusion: Partial Pass**
- **Rationale:** templates show consistent layout hierarchy, cards, spacing, filters, and interaction affordances; cannot confirm runtime rendering/responsiveness statically.
- **Evidence:** `src/main/resources/templates/home/search.html:16`, `src/main/resources/templates/home/index.html:86`, `src/main/resources/templates/fragments/nav.html:11`, `src/main/resources/static/js/app.js:135`
- **Manual verification note:** responsive behavior and cross-browser rendering require manual UI run.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker/High

1) **Severity: High**  
**Title:** Thymeleaf frontend does not consume REST over HTTP as required  
**Conclusion:** Fail  
**Evidence:** `src/main/java/com/campusstore/web/client/InternalApiClient.java:24`, `src/main/java/com/campusstore/web/client/InternalApiClient.java:73`, `src/main/java/com/campusstore/web/controller/SearchController.java:63`  
**Impact:** architecture contract in prompt is broken; API/web parity and boundary testing are weakened, and distributed separation is harder.  
**Minimum actionable fix:** replace `InternalApiClient` service delegation with actual HTTP client calls to `/api/**` (or explicitly revise requirement and docs if monolith-only is intended).

2) **Severity: High**  
**Title:** Profile contact management missing from Thymeleaf UX  
**Conclusion:** Fail  
**Evidence:** `src/main/resources/templates/profile/index.html:80`, `src/main/resources/templates/profile/addresses.html:1`, `src/main/java/com/campusstore/api/controller/ProfileController.java:136`  
**Impact:** explicit requirement "profile area allows users to maintain contacts" is not delivered through role-specific templates.  
**Minimum actionable fix:** add contact list/create/update/delete pages and navigation in profile templates, wired through controller/API flow.

3) **Severity: High**  
**Title:** README production bootstrap-password flow contradicts implementation  
**Conclusion:** Fail  
**Evidence:** `README.md:65`, `README.md:66`, `src/main/java/com/campusstore/infrastructure/config/SeedDataInitializer.java:96`, `src/main/java/com/campusstore/infrastructure/config/SeedDataInitializer.java:99`  
**Impact:** operators may follow docs and fail to initialize admin credentials in production profiles.  
**Minimum actionable fix:** align docs/code (either allow controlled prod path in code or remove prod instructions from README and provide exact supported flow).

### Medium

4) **Severity: Medium**  
**Title:** Anonymous actuator health endpoint bypasses stated rate-limit policy  
**Conclusion:** Partial Fail  
**Evidence:** `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:119`, `src/main/java/com/campusstore/infrastructure/security/filter/RateLimitFilter.java:59`  
**Impact:** explicit prompt policy "anonymous endpoints 30 req/min" is not uniformly enforced.  
**Minimum actionable fix:** include actuator endpoints in rate limiting (or explicitly scope policy exclusions in requirements/docs).

5) **Severity: Medium**  
**Title:** DND API lacks input validation constraints  
**Conclusion:** Partial Fail  
**Evidence:** `src/main/java/com/campusstore/api/dto/DndRequest.java:5`, `src/main/java/com/campusstore/api/controller/UserPreferenceController.java:35`, `src/main/java/com/campusstore/core/service/ProfileService.java:254`  
**Impact:** accepts invalid/null windows without explicit API feedback; behavior is implicit rather than governed.  
**Minimum actionable fix:** add bean validation and semantic checks (null pairing, optional same-time policy, format constraints) with explicit error responses.

## 6. Security Review Summary
- **Authentication entry points: Pass** — form login + `/api/auth/login` implemented; account status checked in user-details service. Evidence: `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:78`, `src/main/java/com/campusstore/infrastructure/security/service/CampusUserDetailsService.java:44`.
- **Route-level authorization: Pass** — default authenticated on API/web, admin/teacher constraints present. Evidence: `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:83`, `src/main/java/com/campusstore/api/controller/UserAdminController.java:34`.
- **Object-level authorization: Pass** — request/profile/notification ownership checks enforced in services. Evidence: `src/main/java/com/campusstore/core/service/RequestService.java:503`, `src/main/java/com/campusstore/core/service/ProfileService.java:109`, `src/main/java/com/campusstore/core/service/NotificationService.java:129`.
- **Function-level authorization: Partial Pass** — many method guards exist, but web adapter bypasses controller-level authorization boundaries by invoking services directly. Evidence: `src/main/java/com/campusstore/web/client/InternalApiClient.java:24`, `src/main/java/com/campusstore/api/controller/WarehouseController.java:32`.
- **Tenant/user isolation: Pass** — user-scoped queries and ownership checks are implemented and tested. Evidence: `src/main/java/com/campusstore/core/service/SearchService.java:282`, `src/test/java/com/campusstore/integration/persistence/CrossUserIsolationPersistenceTest.java:79`.
- **Admin/internal/debug protection: Partial Pass** — admin endpoints guarded; health endpoint intentionally public and excluded from rate limit. Evidence: `src/main/java/com/campusstore/api/controller/CrawlerController.java:30`, `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:119`, `src/main/java/com/campusstore/infrastructure/security/filter/RateLimitFilter.java:60`.

## 7. Tests and Logging Review
- **Unit tests: Pass** — meaningful unit coverage exists for encryption, rate limiting, request/profile authorization, notification DND, warehouse algorithms. Evidence: `src/test/java/com/campusstore/unit/infrastructure/AesEncryptionServiceTest.java:38`, `src/test/java/com/campusstore/unit/infrastructure/RateLimitFilterTest.java:133`, `src/test/java/com/campusstore/unit/service/WarehousePickStrategyTest.java:61`.
- **API/integration tests: Partial Pass** — many API security checks exist, but a large subset uses mocked services, reducing detection of deep service/data faults. Evidence: `src/test/java/com/campusstore/integration/api/AdminApiTest.java:58`, `src/test/java/com/campusstore/integration/persistence/AuthorizationAndGatePersistenceTest.java:63`.
- **Logging categories/observability: Pass** — dedicated audit logger and app logs configured; crawler metrics/alerts logged. Evidence: `src/main/resources/logback-spring.xml:46`, `src/main/java/com/campusstore/core/service/CrawlerService.java:291`.
- **Sensitive-data leakage risk: Partial Pass** — many sensitive fields are ignored/redacted, but some logs include usernames and account identifiers (expected operationally) and should be monitored by policy. Evidence: `src/main/java/com/campusstore/infrastructure/persistence/entity/UserEntity.java:35`, `src/main/java/com/campusstore/core/service/AuditService.java:95`, `src/main/java/com/campusstore/infrastructure/security/service/CampusUserDetailsService.java:40`.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- **Unit tests exist:** yes (`@Tag("unit")`).
- **Integration/API tests exist:** yes (`@Tag("integration")`), plus non-mocked persistence integration tests.
- **E2E tests exist:** yes (`@Tag("e2e")`, Playwright).
- **Frameworks:** JUnit 5 + Spring Boot Test + MockMvc + Mockito + Playwright.
- **Test entry points/docs:** `README.md:74`, `run_tests.sh:11`, `run_tests.sh:47`.
- **Evidence:** `pom.xml:100`, `pom.xml:131`, `src/test/java/com/campusstore/e2e/LoginE2ETest.java:18`.

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Auth required for protected APIs | `src/test/java/com/campusstore/integration/security/SecurityConfigTest.java:103` | 401/403 assertions by role | basically covered | Mostly controller-level | Add non-mocked auth tests on real services for critical endpoints |
| Request object-level authorization | `src/test/java/com/campusstore/unit/service/RequestServiceAuthorizationTest.java:72`, `src/test/java/com/campusstore/integration/persistence/AuthorizationAndGatePersistenceTest.java:81` | Throws `AccessDeniedException` for non-assigned users | sufficient | Limited API-level object auth tests | Add API tests that assert forbidden detail access end-to-end |
| Profile cross-user isolation | `src/test/java/com/campusstore/unit/service/ProfileServiceAuthorizationTest.java:58`, `src/test/java/com/campusstore/integration/persistence/CrossUserIsolationPersistenceTest.java:90` | non-owner update/delete denied | sufficient | Contact API endpoint response shape not verified | Add API-level contact CRUD + isolation tests |
| Notification DND + critical override | `src/test/java/com/campusstore/unit/service/NotificationDndTest.java:63` | critical bypass and suppression assertions | basically covered | No integration tests for scheduler-triggered notifications | Add integration tests for overdue/missed-checkin scheduler outputs |
| Rate limiting policy + escalation | `src/test/java/com/campusstore/unit/infrastructure/RateLimitFilterTest.java:133` | 429 + Retry-After + lockout checks | basically covered | No integration test that health endpoint bypass behavior is intentional | Add security-policy test for anonymous endpoint budget including exclusions |
| Search public/private behavior | `src/test/java/com/campusstore/integration/api/SearchApiTest.java:97` | public search 200, history 401 unauth | basically covered | No strong assertions for personalization ranking/distance correctness | Add service-level deterministic ranking tests with fixtures |
| Warehouse strategy simulation schema | `src/test/java/com/campusstore/unit/service/WarehouseSimulationSchemaTest.java:69`, `src/test/java/com/campusstore/integration/api/AdminApiTest.java:282` | required response keys asserted | sufficient | Runtime performance/large-data behavior untested | Add load-ish simulation tests with larger pick datasets |
| Encryption-at-rest for contact fields | `src/test/java/com/campusstore/integration/persistence/RequestLifecycleAndContactsPersistenceTest.java:147` | decrypt roundtrip and encrypted column checks | sufficient | No API contract test for masked/plain exposure policy | Add API tests for phone masking and DTO exposure boundaries |
| Retention + cryptographic erasure | `src/test/java/com/campusstore/unit/service/CryptographicErasureTest.java:75` | placeholder mapping + deletion + open-dispute block | basically covered | No scheduled-job integration run coverage | Add integration test invoking scheduled methods against test DB |

### 8.3 Security Coverage Audit
- **Authentication:** basically covered (security integration tests), but mostly mocked service internals.
- **Route authorization:** basically covered for many admin/teacher/student paths.
- **Object-level authorization:** sufficiently covered in request/profile/notification service tests with non-mocked persistence checks.
- **Tenant/data isolation:** basically covered (cross-user isolation tests) but not across every feature (e.g., favorites/history API shape).
- **Admin/internal protection:** basically covered for admin APIs; actuator/rate-limit policy edge not explicitly safeguarded by tests.

### 8.4 Final Coverage Judgment
- **Partial Pass**
- Major authorization/encryption flows are covered reasonably well, but heavy controller mocking plus gaps around policy-edge cases and full REST-boundary behavior mean severe defects could still remain undetected while tests pass.

## 9. Final Notes
- This audit is static-only; no runtime success claims are made.
- Strongest risks are requirement-fit and architecture-boundary mismatches, not basic repository completeness.
- Manual verification should focus on deployment bootstrap behavior, true frontend/backend API boundary behavior, and browser/runtime UX completeness.
