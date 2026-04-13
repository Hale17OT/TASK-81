# Issue Re-Verification (Round 2, Static)

Date: 2026-04-13  
Boundary: static-only (no runtime execution)

## Overall Result
- Fixed: **5 / 5**
- Unfixed: **0 / 5**

## 1) High — Thymeleaf frontend does not consume REST over HTTP
- **Status:** **Fixed**
- **Conclusion:** `InternalApiClient` is now HTTP-based using `RestClient`, with loopback calls to application `/api/**` endpoints and request forwarding.
- **Evidence:**
  - `src/main/java/com/campusstore/web/client/InternalApiClient.java:25`
  - `src/main/java/com/campusstore/web/client/InternalApiClient.java:56`
  - `src/main/java/com/campusstore/web/client/RestClientConfig.java:74`
  - `src/main/java/com/campusstore/web/controller/SearchController.java:63`

## 2) High — Profile contact management missing from Thymeleaf UX
- **Status:** **Fixed**
- **Conclusion:** Contact management navigation, view, and web CRUD handlers are present.
- **Evidence:**
  - `src/main/resources/templates/profile/index.html:86`
  - `src/main/resources/templates/profile/contacts.html:1`
  - `src/main/java/com/campusstore/web/controller/ProfileWebController.java:94`
  - `src/main/java/com/campusstore/web/controller/ProfileWebController.java:106`

## 3) High — README production bootstrap-password flow contradicts implementation
- **Status:** **Fixed**
- **Conclusion:** README production path (`ADMIN_INITIAL_PASSWORD`) aligns with implementation allowing explicit password in any profile, while generator remains profile-restricted.
- **Evidence:**
  - `README.md:65`
  - `src/main/java/com/campusstore/infrastructure/config/SeedDataInitializer.java:42`
  - `src/main/java/com/campusstore/infrastructure/config/SeedDataInitializer.java:95`
  - `src/main/java/com/campusstore/infrastructure/config/SeedDataInitializer.java:103`

## 4) Medium — Anonymous actuator health endpoint bypasses stated rate-limit policy
- **Status:** **Fixed**
- **Conclusion:** Rate-limit filter now explicitly does not skip actuator paths; only static assets are bypassed.
- **Evidence:**
  - `src/main/java/com/campusstore/infrastructure/security/filter/RateLimitFilter.java:57`
  - `src/main/java/com/campusstore/infrastructure/security/filter/RateLimitFilter.java:61`
  - `src/main/java/com/campusstore/infrastructure/security/config/SecurityConfig.java:119`

## 5) Medium — DND API lacks input validation constraints
- **Status:** **Fixed**
- **Conclusion:** DND request now has semantic validation (`@AssertTrue`) and controller enforces validation via `@Valid`.
- **Evidence:**
  - `src/main/java/com/campusstore/api/dto/DndRequest.java:41`
  - `src/main/java/com/campusstore/api/dto/DndRequest.java:47`
  - `src/main/java/com/campusstore/api/controller/UserPreferenceController.java:38`

## Note
- This result confirms static code/doc alignment for the five reported findings only.
