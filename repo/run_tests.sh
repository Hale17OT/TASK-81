#!/bin/bash
set -e

echo "============================================"
echo "  CampusStore Test Suite"
echo "============================================"

# ---- Unit Tests ----
# JUnit 5 tag selection — see pom.xml properties test.tags.included / test.tags.excluded.
# These values are passed straight to Surefire's <groups>/<excludedGroups>, which Surefire
# 3.x maps to JUnit 5 tag expressions when the Jupiter engine is on the classpath.
echo ""
echo ">>> Running unit tests..."
./mvnw test -Dtest.tags.included=unit -Dtest.tags.excluded= -pl .

# ---- Integration Tests ----
echo ""
echo ">>> Running integration tests..."
./mvnw test -Dtest.tags.included=integration -Dtest.tags.excluded= -pl .

# ---- Build Docker Test Environment ----
# Reuses the project's docker-compose.yml (mysql + app) so E2E exercises the same image
# that ships to operators. MASTER_KEY_PASSPHRASE / SERVER_SSL_KEY_STORE_PASSWORD have CI
# defaults baked into the compose file; production deployments override them.
echo ""
echo ">>> Cleaning up any prior containers/volumes (idempotent)..."
docker compose down -v --remove-orphans 2>/dev/null || true

echo ""
echo ">>> Building and starting Docker environment..."
docker compose up -d --build

# ---- Wait for App to be Ready ----
echo ""
echo ">>> Waiting for application to be ready..."
MAX_RETRIES=60
RETRY_INTERVAL=5
RETRY_COUNT=0

until curl --insecure --silent --fail https://localhost:8443/actuator/health > /dev/null 2>&1; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ "$RETRY_COUNT" -ge "$MAX_RETRIES" ]; then
        echo "ERROR: Application did not become ready within $((MAX_RETRIES * RETRY_INTERVAL)) seconds."
        docker compose down -v
        exit 1
    fi
    echo "  Waiting for app... (attempt $RETRY_COUNT/$MAX_RETRIES)"
    sleep "$RETRY_INTERVAL"
done

echo "Application is ready."

# ---- E2E Tests ----
# Ensure Playwright browser binaries are present. Auto-download from cdn.playwright.dev
# can stall on first run; this explicit install fails fast with a clearer message AND
# becomes a no-op on subsequent runs because the cache is already populated.
echo ""
echo ">>> Installing Playwright browsers (no-op if cached)..."
./mvnw -q exec:java -e -Dexec.classpathScope=test \
    -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium" \
    || echo "  (Playwright install reported non-zero — proceeding; tests may still pass if cache exists)"

echo ""
echo ">>> Running E2E tests..."
E2E_EXIT_CODE=0
./mvnw test -Dtest.tags.included=e2e -Dtest.tags.excluded= -pl . || E2E_EXIT_CODE=$?

# ---- Tear Down ----
echo ""
echo ">>> Tearing down Docker test environment..."
docker compose down -v

if [ "$E2E_EXIT_CODE" -ne 0 ]; then
    echo "E2E tests failed."
    exit "$E2E_EXIT_CODE"
fi

echo ""
echo "============================================"
echo "  ALL TESTS PASSED"
echo "============================================"
