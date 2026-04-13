# Recheck of 5 Previously Reported Issues (Current Revision)

Static-only verification; no runtime execution performed.

## Outcome
- Fixed: 5
- Not fixed: 0

## Issue-by-issue status

1) **High — User hard-delete workflow vs dispute FK behavior**
- **Status:** Fixed
- **Why:** Deletion flow now anonymizes closed disputes before delete, and schema migration changes dispute FK behavior to allow user deletion without violating the open-dispute rule.
- **Evidence:** `src/main/java/com/campusstore/core/service/AuditService.java:217`, `src/main/java/com/campusstore/core/service/AuditService.java:222`, `src/main/resources/db/migration/V5__dispute_placeholder_anonymization.sql:23`, `src/main/resources/db/migration/V5__dispute_placeholder_anonymization.sql:29`, `src/main/java/com/campusstore/infrastructure/persistence/repository/DisputeRepository.java:31`

2) **High — Missing admin policy-management capability**
- **Status:** Fixed
- **Why:** Admin policy management now exists in both API and Thymeleaf UI, with nav entry and update flow.
- **Evidence:** `src/main/java/com/campusstore/api/controller/PolicyAdminController.java:33`, `src/main/java/com/campusstore/api/controller/PolicyAdminController.java:51`, `src/main/java/com/campusstore/web/controller/AdminWebController.java:284`, `src/main/resources/templates/admin/policies.html:1`, `src/main/resources/templates/fragments/nav.html:75`, `README.md:215`

3) **High — Warehouse admin page paged model binding**
- **Status:** Fixed
- **Why:** Template now iterates `locations.content`, matching controller model type `PagedResponse`.
- **Evidence:** `src/main/java/com/campusstore/web/controller/AdminWebController.java:224`, `src/main/resources/templates/warehouse/index.html:18`

4) **Medium — Test phase selection misconfigured for JUnit 5 tags**
- **Status:** Fixed (static evidence)
- **Why:** Test script and Maven config now use unified tag properties (`test.tags.included` / `test.tags.excluded`) mapped through surefire configuration.
- **Evidence:** `run_tests.sh:14`, `run_tests.sh:19`, `run_tests.sh:50`, `pom.xml:36`, `pom.xml:37`, `pom.xml:174`, `pom.xml:175`, `src/test/java/com/campusstore/e2e/LoginE2ETest.java:18`
- **Manual verification required:** execute the tagged commands in CI/local to confirm actual surefire/JUnit selection behavior in this environment.

5) **Medium — DND clear flow payload semantics**
- **Status:** Fixed
- **Why:** Frontend now normalizes empty time inputs to `null`, validates pairing client-side, and provides explicit `Clear DND` action; backend DTO keeps null/null clear semantics.
- **Evidence:** `src/main/resources/templates/profile/notification-settings.html:53`, `src/main/resources/templates/profile/notification-settings.html:61`, `src/main/resources/templates/profile/notification-settings.html:67`, `src/main/resources/templates/profile/notification-settings.html:70`, `src/main/java/com/campusstore/api/dto/DndRequest.java:10`, `src/main/java/com/campusstore/api/dto/DndRequest.java:41`
