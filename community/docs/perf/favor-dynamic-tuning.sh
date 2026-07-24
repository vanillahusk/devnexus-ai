#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
CHANNEL="dynamic-tp:refresh"
THREADS="${THREADS:-8}"
CONNECTIONS="${CONNECTIONS:-200}"
DURATION="${DURATION:-90s}"

duration_seconds() {
  local value="$1"
  if [[ "${value}" =~ ^([0-9]+)s$ ]]; then
    echo "${BASH_REMATCH[1]}"
  elif [[ "${value}" =~ ^([0-9]+)m$ ]]; then
    echo "$((BASH_REMATCH[1] * 60))"
  elif [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${value}"
  else
    echo "90"
  fi
}

publish_refresh() {
  local payload="$1"
  local subscribers
  subscribers=$(${REDIS_CLI} PUBLISH ${CHANNEL} "${payload}" | awk '{print $NF}')
  if [[ "${subscribers}" == "0" ]]; then
    echo "[ERROR] 动态调参消息未被消费（channel=${CHANNEL}, subscribers=0），当前环境可能未启用 Redis 通道刷新。"
    echo "[ERROR] 建议改用 baseline 压测，或先确认动态线程池刷新通道配置。"
    exit 1
  fi
}

if ! command -v wrk >/dev/null 2>&1; then
  echo "wrk 未安装，请先安装 wrk"
  exit 1
fi

if ! command -v "${REDIS_CLI%% *}" >/dev/null 2>&1; then
  echo "redis-cli 未安装，请先安装 redis-cli"
  exit 1
fi

echo "[1/4] 启动 ${DURATION} 压测..."
wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" --latency -s docs/perf/favor.lua "${BASE_URL}" > /tmp/favor-wrk-dynamic.log 2>&1 &
WRK_PID=$!

duration_sec="$(duration_seconds "${DURATION}")"
first_delay=$((duration_sec / 3))
second_delay=$((duration_sec / 3))
last_delay=$((duration_sec - first_delay - second_delay))
if [[ "${first_delay}" -lt 1 ]]; then first_delay=1; fi
if [[ "${second_delay}" -lt 1 ]]; then second_delay=1; fi
if [[ "${last_delay}" -lt 1 ]]; then last_delay=1; fi

sleep "${first_delay}"
echo "[2/4] 动态扩容 favorPersistExecutor"
publish_refresh '{"poolName":"favorPersistExecutor","coreSize":8,"maxSize":24,"queueCapacity":4096}'

sleep "${second_delay}"
echo "[3/4] 动态扩容 favorNotifyExecutor"
publish_refresh '{"poolName":"favorNotifyExecutor","coreSize":8,"maxSize":16,"queueCapacity":2048}'

sleep "${last_delay}"
echo "[4/4] 回滚线程池到默认参数"
publish_refresh '{"poolName":"favorPersistExecutor","coreSize":4,"maxSize":16,"queueCapacity":2048}'
publish_refresh '{"poolName":"favorNotifyExecutor","coreSize":4,"maxSize":12,"queueCapacity":1024}'

wait ${WRK_PID}
echo "压测完成，结果文件: /tmp/favor-wrk-dynamic.log"
cat /tmp/favor-wrk-dynamic.log
