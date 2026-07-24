#!/usr/bin/env bash
set -euo pipefail

GROUP="${1:-paicoding-comment-write-group}"
MESSAGE_KEY="${2:-}"
DLQ_TOPIC="%DLQ%${GROUP}"

echo "DLQ topic: ${DLQ_TOPIC}"
docker exec paicoding-rocketmq-broker sh mqadmin topicStatus \
  -n namesrv:9876 -t "${DLQ_TOPIC}"

if [[ -n "${MESSAGE_KEY}" ]]; then
  echo "Query key: ${MESSAGE_KEY}"
  docker exec paicoding-rocketmq-broker sh mqadmin queryMsgByKey \
    -n namesrv:9876 -t "${DLQ_TOPIC}" -k "${MESSAGE_KEY}"
fi
