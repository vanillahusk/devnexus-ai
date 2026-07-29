#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/ops/ragent/docker-compose.yml"
PROJECT_NAME="paicoding-ragent-low-resource"
MIN_AVAILABLE_MEMORY_MB="${RAGENT_INFRA_MIN_AVAILABLE_MEMORY_MB:-2200}"
MAX_WAIT_SECONDS="${RAGENT_INFRA_MAX_WAIT_SECONDS:-150}"
USE_EXTERNAL_REDIS="${RAGENT_USE_EXTERNAL_REDIS:-false}"
MANAGED_REDIS_PORT="${RAGENT_MANAGED_REDIS_PORT:-16379}"
COMPOSE_PROFILES=(--profile ragent)
OWNED_CONTAINERS=(
  paicoding-ragent-postgres
  paicoding-ragent-object-storage
  paicoding-ragent-rocketmq-nameserver
  paicoding-ragent-rocketmq-broker
)
if [[ "${USE_EXTERNAL_REDIS}" != "true" ]]; then
  COMPOSE_PROFILES+=(--profile managed-redis)
  OWNED_CONTAINERS=(paicoding-ragent-redis "${OWNED_CONTAINERS[@]}")
fi

cleanup_on_failure() {
  local exit_code=$?
  if [[ "${exit_code}" -ne 0 ]]; then
    echo "Ragent dependency startup failed; cleaning only this Compose project." >&2
    docker-compose "${COMPOSE_PROFILES[@]}" -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" down --remove-orphans >/dev/null 2>&1 || true
  fi
}
trap cleanup_on_failure EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 1
  }
}

require_command docker
require_command docker-compose
require_command nc

if [[ "${USE_EXTERNAL_REDIS}" == "true" ]]; then
  require_command redis-cli
  if [[ "$(redis-cli -h 127.0.0.1 -p 6379 PING 2>/dev/null | tail -n 1)" != "PONG" ]]; then
    echo "RAGENT_USE_EXTERNAL_REDIS=true, but Redis PING failed at 127.0.0.1:6379." >&2
    exit 1
  fi
  echo "Using healthy external Redis at 127.0.0.1:6379; it will not be stopped or removed."
fi

RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
export RAGENT_PROJECT_DIR
for required_file in \
  "${RAGENT_PROJECT_DIR}/resources/database/schema_pg.sql" \
  "${RAGENT_PROJECT_DIR}/resources/database/init_data_pg.sql"; do
  if [[ ! -r "${required_file}" ]]; then
    echo "Missing associated Ragent database file: ${required_file}" >&2
    exit 1
  fi
done

available_memory_mb="$(awk '/MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo)"
if [[ -z "${available_memory_mb}" || "${available_memory_mb}" -lt "${MIN_AVAILABLE_MEMORY_MB}" ]]; then
  echo "Insufficient memory: ${available_memory_mb:-unknown}MB available, ${MIN_AVAILABLE_MEMORY_MB}MB required." >&2
  exit 1
fi

ports=(5432 9000 9876 10911)
if [[ "${USE_EXTERNAL_REDIS}" != "true" ]]; then
  ports=("${MANAGED_REDIS_PORT}" "${ports[@]}")
fi
for port in "${ports[@]}"; do
  if nc -z -w 1 127.0.0.1 "${port}" >/dev/null 2>&1; then
    echo "Port ${port} is already occupied; refusing to replace or mix with an unmanaged dependency." >&2
    exit 1
  fi
done

docker-compose "${COMPOSE_PROFILES[@]}" -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" config >/dev/null
docker-compose "${COMPOSE_PROFILES[@]}" -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d

deadline=$((SECONDS + MAX_WAIT_SECONDS))
while ((SECONDS < deadline)); do
  all_healthy=true
  for container_name in "${OWNED_CONTAINERS[@]}"; do
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "${container_name}" 2>/dev/null || echo missing)"
    if [[ "${health}" != "healthy" ]]; then
      all_healthy=false
      break
    fi
  done
  if [[ "${all_healthy}" == "true" ]]; then
    echo "Ragent low-resource dependencies are healthy."
    if [[ "${USE_EXTERNAL_REDIS}" == "true" ]]; then
      redis_endpoint="127.0.0.1:6379 (external=true)"
    else
      redis_endpoint="127.0.0.1:${MANAGED_REDIS_PORT} (external=false)"
    fi
    echo "Redis=${redis_endpoint} PostgreSQL=127.0.0.1:5432 S3=127.0.0.1:9000 RocketMQ=127.0.0.1:9876"
    exit 0
  fi
  sleep 2
done

echo "Ragent dependencies did not become healthy within ${MAX_WAIT_SECONDS}s." >&2
for container_name in "${OWNED_CONTAINERS[@]}"; do
  docker logs --tail 80 "${container_name}" 2>&1 || true
done
exit 1
