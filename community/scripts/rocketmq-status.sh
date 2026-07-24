#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/ops/rocketmq/docker-compose.yml"

docker-compose -f "${COMPOSE_FILE}" ps
docker exec paicoding-rocketmq-broker sh mqadmin clusterList -n namesrv:9876
docker exec paicoding-rocketmq-broker sh mqadmin topicStatus \
  -n namesrv:9876 -t "${ROCKETMQ_TOPIC:-paicoding-business-event}"
