#!/usr/bin/env bash
set -euo pipefail

BROKER_CONTAINER="${BROKER_CONTAINER:-paicoding-rocketmq-broker}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-pai_coding}"
ACTUATOR_URL="${ACTUATOR_URL:-http://127.0.0.1:8081/actuator/prometheus}"
EVENT_ID="fault-drill-$(date +%Y%m%d-%H%M%S)"
BROKER_PAUSED=0

mysql_exec() {
  MYSQL_PWD="${MYSQL_PASSWORD}" mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" \
    -u"${MYSQL_USER}" -N "${MYSQL_DATABASE}" -e "$1"
}

restore_broker() {
  if [[ "${BROKER_PAUSED}" -eq 1 ]]; then
    docker unpause "${BROKER_CONTAINER}" >/dev/null 2>&1 || true
  fi
}
trap restore_broker EXIT INT TERM

curl -fsS "${ACTUATOR_URL}" >/dev/null
docker inspect -f '{{.State.Status}}' "${BROKER_CONTAINER}" | grep -qx running

echo "[1/5] pause RocketMQ broker: ${BROKER_CONTAINER}"
docker pause "${BROKER_CONTAINER}" >/dev/null
BROKER_PAUSED=1

echo "[2/5] create isolated outbox event: ${EVENT_ID}"
mysql_exec "INSERT INTO mq_outbox_event(event_id,topic,tag,aggregate_id,payload,status,retry_count,next_retry_time,last_error,create_time,update_time) VALUES('${EVENT_ID}','paicoding-business-event','fault-drill','fault-drill','{\"eventVersion\":1,\"eventId\":\"${EVENT_ID}\",\"occurredAt\":$(date +%s000),\"notifyType\":\"SYSTEM\",\"content\":{},\"userId\":0}',0,0,NULL,'',NOW(),NOW());"

echo "[3/5] wait for RETRY evidence"
for _ in $(seq 1 20); do
  ROW="$(mysql_exec "SELECT CONCAT(status,':',retry_count,':',last_error) FROM mq_outbox_event WHERE event_id='${EVENT_ID}';")"
  if [[ "${ROW}" == 2:* || "${ROW}" == 1:*:*timeout* ]]; then
    echo "failure-stage=${ROW}"
    break
  fi
  sleep 1
done
if [[ "${ROW}" != 2:* && "${ROW}" != 1:*:*timeout* ]]; then
  echo "Outbox event did not enter retry flow: ${ROW}" >&2
  exit 1
fi

echo "[4/5] restore broker and wait for SENT"
docker unpause "${BROKER_CONTAINER}" >/dev/null
BROKER_PAUSED=0
for _ in $(seq 1 90); do
  ROW="$(mysql_exec "SELECT CONCAT(status,':',retry_count,':',TIMESTAMPDIFF(SECOND,create_time,update_time)) FROM mq_outbox_event WHERE event_id='${EVENT_ID}';")"
  if [[ "${ROW}" == 3:* ]]; then
    echo "recovery-stage=${ROW}"
    break
  fi
  sleep 1
done
if [[ "${ROW}" != 3:* ]]; then
  echo "Outbox event was not recovered within 90 seconds: ${ROW}" >&2
  exit 1
fi

echo "[5/5] Prometheus evidence"
curl -fsS "${ACTUATOR_URL}" \
  | grep -E '^(mq_outbox_events|mq_outbox_dispatch_total|mq_outbox_delivery_latency_seconds_(count|sum|max))'
echo "PASS eventId=${EVENT_ID} (status: 0=pending, 1=sending, 2=retry, 3=sent)"
