#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/ops/ragent/docker-compose.yml"
PROJECT_NAME="paicoding-ragent-low-resource"

docker-compose --profile ragent --profile managed-redis -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}" ps
