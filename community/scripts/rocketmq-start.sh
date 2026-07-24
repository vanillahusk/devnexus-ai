#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/ops/rocketmq/docker-compose.yml"
TOPIC="${ROCKETMQ_TOPIC:-paicoding-business-event}"

# docker-compose 1.29 与新版 Docker 在原地 recreate 时可能触发 ContainerConfig 错误。
# 只删除本编排的容器和网络，命名数据卷会保留。
docker-compose -f "${COMPOSE_FILE}" down --remove-orphans
if [[ "${ROCKETMQ_LOW_RESOURCE_MODE:-false}" == "true" ]]; then
  docker-compose -f "${COMPOSE_FILE}" up -d namesrv broker
else
  docker-compose -f "${COMPOSE_FILE}" up -d
fi

echo "Waiting for RocketMQ broker..."
for attempt in $(seq 1 30); do
  if docker exec paicoding-rocketmq-broker sh mqadmin clusterList -n namesrv:9876 >/dev/null 2>&1; then
    BROKER_IP="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' paicoding-rocketmq-broker)"
    docker exec paicoding-rocketmq-broker sh mqadmin updateTopic \
      -n namesrv:9876 -b "${BROKER_IP}:10911" -t "${TOPIC}" -r 8 -w 8 >/dev/null
    if [[ "${ROCKETMQ_LOW_RESOURCE_MODE:-false}" == "true" ]]; then
      echo "RocketMQ is ready: nameServer=127.0.0.1:9876 topic=${TOPIC} dashboard=disabled(low-resource)"
    else
      echo "RocketMQ is ready: nameServer=127.0.0.1:9876 topic=${TOPIC} dashboard=http://127.0.0.1:8082"
    fi
    exit 0
  fi
  sleep 2
done

echo "RocketMQ broker did not become ready in time." >&2
docker-compose -f "${COMPOSE_FILE}" ps
docker logs --tail 100 paicoding-rocketmq-broker
exit 1
