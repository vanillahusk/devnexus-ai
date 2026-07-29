#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
REDIS_HOST="${RAGENT_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${RAGENT_REDIS_PORT:-6379}"
TIMEOUT_SECONDS="${RAGENT_AGENT_QUOTA_TIMEOUT_SECONDS:-180}"

[[ -d "${RAGENT_PROJECT_DIR}/bootstrap" ]] || {
  echo "[FAIL] Ragent project is missing: ${RAGENT_PROJECT_DIR}" >&2
  exit 1
}
command -v redis-cli >/dev/null 2>&1 || {
  echo "[FAIL] redis-cli is unavailable" >&2
  exit 1
}
timeout 3s redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ping | rg -qx 'PONG' || {
  echo "[FAIL] Redis is not ready at ${REDIS_HOST}:${REDIS_PORT}; no service was started" >&2
  exit 1
}

cd "${RAGENT_PROJECT_DIR}"
RAGENT_REAL_REDIS_QUOTA=true \
RAGENT_REDIS_HOST="${REDIS_HOST}" \
RAGENT_REDIS_PORT="${REDIS_PORT}" \
MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx512m -XX:+UseSerialGC}" \
timeout --signal=TERM --kill-after=20s "${TIMEOUT_SECONDS}" \
  mvn -o -pl bootstrap -am \
  -Dspotless.apply.skip=true \
  -DskipITs \
  -Dtest=RedisAgentQuotaRealIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

echo "[OK] real Redis Agent quota validation passed; isolated keys were removed"
