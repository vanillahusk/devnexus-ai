#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
ADMIN_COOKIE="${ADMIN_COOKIE:-}"

if [[ -z "${ADMIN_COOKIE}" ]]; then
  echo "ADMIN_COOKIE is required, for example: ADMIN_COOKIE='f-session=xxx' bash $0" >&2
  exit 1
fi

timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
echo "backend reliability evidence @ ${timestamp}"

echo "[health]"
curl -fsS "${BASE_URL}/actuator/health"
echo

echo "[favor queues via admin API]"
curl -fsS -H "Cookie: ${ADMIN_COOKIE}" "${BASE_URL}/api/admin/favor/reliability/status"
echo

echo "[ai circuit breakers]"
curl -fsS -H "Cookie: ${ADMIN_COOKIE}" "${BASE_URL}/api/admin/ai/governance/circuits"
echo

echo "[dynamic thread pools]"
curl -fsS "${BASE_URL}/actuator/dynamicThreadPools"
echo

echo "[favor redis queue lengths]"
for key in \
  favor:event:queue \
  favor:event:processing:queue \
  favor:persist:retry:queue \
  favor:persist:retry:processing:queue \
  favor:persist:dead:queue \
  favor:notify:retry:queue \
  favor:notify:retry:processing:queue \
  favor:notify:dead:queue
do
  size="$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" LLEN "${key}")"
  echo "${key}=${size}"
done

echo "[prometheus samples]"
curl -fsS "${BASE_URL}/actuator/prometheus" \
  | grep -E 'favor_executor|dynamic_tp|http_server_requests' \
  | head -n 80 || true
