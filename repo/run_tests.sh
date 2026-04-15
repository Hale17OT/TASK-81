#!/bin/bash
set -e

# ============================================
#   CampusStore Test Suite
# ============================================
#
# Usage:
#   ./run_tests.sh              # Default: unit + integration tests on host JVM,
#                               #          then E2E tests against Docker environment.
#
#   ./run_tests.sh --docker     # Fully-Dockerised: unit + integration tests run
#                               #          inside a Maven container (no host Java
#                               #          required), then E2E as above.
#
# Prerequisites (default mode):  Docker (auto-falls back to containerised if no host Java);
#                                Java 17+ and Maven wrapper (./mvnw) for the fast host path.
# Prerequisites (--docker mode): Docker only
#
# Environment variables used by docker-compose.yml (required at E2E stage):
#   MASTER_KEY_PASSPHRASE              – AES-256 master key (default: TestPassphrase2026)
#   SERVER_SSL_KEY_STORE_PASSWORD      – TLS keystore password (default: changeit)
# ============================================

DOCKER_MODE=false
if [ "${1:-}" = "--docker" ]; then
  DOCKER_MODE=true
fi

# ─────────────────────────────────────────────────────────────────────────────
#   Auto-detect: fall back to Docker mode if no host JDK is available.
#   This makes the default mode work in container-only CI environments
#   (e.g., GitHub Actions, Docker-in-Docker) without requiring --docker.
# ─────────────────────────────────────────────────────────────────────────────
if ! $DOCKER_MODE && ! command -v java > /dev/null 2>&1; then
  echo "Note: Java not found on host — switching to fully-Dockerised mode automatically."
  echo "      Pass '--docker' explicitly to suppress this detection."
  DOCKER_MODE=true
fi

# Master key and TLS password for the E2E Docker environment.
# Override via environment variables in CI to avoid hardcoding.
: "${MASTER_KEY_PASSPHRASE:=TestPassphrase2026}"
: "${SERVER_SSL_KEY_STORE_PASSWORD:=changeit}"

echo "============================================"
echo "  CampusStore Test Suite"
if $DOCKER_MODE; then
  echo "  Mode: fully Dockerised (--docker)"
else
  echo "  Mode: host JVM + Docker E2E"
fi
echo "============================================"

# Ensure the Maven wrapper is executable. On a fresh Linux clone of a repo authored on
# Windows the executable bit may be missing; this avoids "./mvnw: Permission denied".
chmod +x ./mvnw 2>/dev/null || true

# ─────────────────────────────────────────────────────────────────────────────
#   Helper: run Maven test phase
# ─────────────────────────────────────────────────────────────────────────────
run_maven_tests() {
  local TAGS="$1"
  if $DOCKER_MODE; then
    # Run tests inside a throwaway Maven container so the host needs no JDK/Maven.
    # Mount the project root and use a named volume for the local Maven repository
    # to avoid re-downloading dependencies on every run.
    docker run --rm \
      --network host \
      -v "$(pwd):/app" \
      -v campusstore-m2-cache:/root/.m2 \
      -w /app \
      maven:3.9-amazoncorretto-17 \
      ./mvnw test \
        -Dtest.tags.included="${TAGS}" \
        -Dtest.tags.excluded= \
        --no-transfer-progress \
        -pl .
  else
    ./mvnw test \
      -Dtest.tags.included="${TAGS}" \
      -Dtest.tags.excluded= \
      -pl .
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
#   Phase 1: Unit Tests
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo ">>> Running unit tests..."
run_maven_tests "unit"

# ─────────────────────────────────────────────────────────────────────────────
#   Phase 2: Integration Tests  (HTTP black-box against embedded H2 app)
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo ">>> Running integration tests..."
run_maven_tests "integration"

# ─────────────────────────────────────────────────────────────────────────────
#   Phase 3: Build Docker E2E Environment
# ─────────────────────────────────────────────────────────────────────────────
# Reuses the project's docker-compose.yml (mysql + app) so E2E exercises the
# same image that ships to operators.
echo ""
echo ">>> Cleaning up any prior containers/volumes (idempotent)..."
docker compose down -v --remove-orphans 2>/dev/null || true

echo ""
echo ">>> Building and starting Docker environment..."
MASTER_KEY_PASSPHRASE="${MASTER_KEY_PASSPHRASE}" \
SERVER_SSL_KEY_STORE_PASSWORD="${SERVER_SSL_KEY_STORE_PASSWORD}" \
docker compose up -d --build

# ─────────────────────────────────────────────────────────────────────────────
#   Phase 3a: Wait for App to be Ready
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo ">>> Waiting for application to be ready..."
MAX_RETRIES=60
RETRY_INTERVAL=5
RETRY_COUNT=0

until curl --insecure --silent --fail https://localhost:8443/actuator/health > /dev/null 2>&1; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ "$RETRY_COUNT" -ge "$MAX_RETRIES" ]; then
        echo "ERROR: Application did not become ready within $((MAX_RETRIES * RETRY_INTERVAL)) seconds."
        docker compose logs --tail=50
        docker compose down -v
        exit 1
    fi
    echo "  Waiting for app... (attempt $RETRY_COUNT/$MAX_RETRIES)"
    sleep "$RETRY_INTERVAL"
done

echo "Application is ready."

# ─────────────────────────────────────────────────────────────────────────────
#   Phase 4: E2E Tests  (Playwright against running Docker app)
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo ">>> Running E2E tests..."
E2E_EXIT_CODE=0
if $DOCKER_MODE; then
  # Run E2E tests inside the official Playwright Java image so no host JDK or
  # browser installation is needed.  --network host lets the container reach the
  # Docker-compose app at https://localhost:8443.  The Playwright image already
  # ships with Chromium and all required system dependencies.
  docker run --rm \
    --network host \
    -v "$(pwd):/app" \
    -v campusstore-m2-cache:/root/.m2 \
    -w /app \
    mcr.microsoft.com/playwright/java:v1.51.0-jammy \
    bash -c "chmod +x ./mvnw && ./mvnw test \
      -Dtest.tags.included=e2e \
      -Dtest.tags.excluded= \
      --no-transfer-progress \
      -pl ." || E2E_EXIT_CODE=$?
else
  # Host-Maven path: ensure Playwright browser binaries are present first.
  # Auto-download from cdn.playwright.dev can stall on first run; this explicit
  # install fails fast with a clearer message and is a no-op when already cached.
  echo ">>> Installing Playwright browsers (no-op if cached)..."
  ./mvnw -q exec:java -e -Dexec.classpathScope=test \
      -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium" \
      || echo "  (Playwright install reported non-zero — proceeding; tests may still pass if cache exists)"

  ./mvnw test -Dtest.tags.included=e2e -Dtest.tags.excluded= -pl . || E2E_EXIT_CODE=$?
fi

# ─────────────────────────────────────────────────────────────────────────────
#   Tear Down
# ─────────────────────────────────────────────────────────────────────────────
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
