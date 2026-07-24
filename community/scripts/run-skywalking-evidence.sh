#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AGENT_JAR="${SKYWALKING_AGENT_DIR:-${ROOT_DIR}/.runtime/skywalking-agent}/skywalking-agent.jar"
OAP_ADDR="${SKYWALKING_OAP_ADDR:-127.0.0.1:11800}"
PIDS=()

cleanup() {
  if ((${#PIDS[@]} > 0)); then
    kill "${PIDS[@]}" 2>/dev/null || true
    wait "${PIDS[@]}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

test -f "${AGENT_JAR}"
cd "${ROOT_DIR}"

mkdir -p /tmp/paicoding-skywalking/{gateway,auth,message,web}

echo '[observability infrastructure]'
docker-compose --profile observability -f ops/docker-compose.yml up -d \
  prometheus grafana \
  skywalking-banyandb skywalking-oap skywalking-ui

wait_oap() {
  local code
  for ((i=1; i<=120; i++)); do
    code="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' \
      http://127.0.0.1:12800/healthcheck 2>/dev/null || true)"
    if [[ "${code}" == "200" ]]; then
      echo "skywalking-oap=ready(http:${code})"
      return 0
    fi
    sleep 1
  done
  echo 'skywalking-oap=timeout' >&2
  return 1
}

wait_oap

start_service() {
  local service_name="$1"
  local log_dir="$2"
  local jar_path="$3"
  shift 3
  java \
    "-javaagent:${AGENT_JAR}" \
    "-Dskywalking.agent.service_name=${service_name}" \
    "-Dskywalking.agent.instance_name=${service_name}-evidence" \
    "-Dskywalking.collector.backend_service=${OAP_ADDR}" \
    "-Dskywalking.logging.dir=${log_dir}" \
    -jar "${jar_path}" "$@" >"${log_dir}/application.log" 2>&1 &
  PIDS+=("$!")
}

start_service auth-service /tmp/paicoding-skywalking/auth \
  paicoding-auth-service/target/paicoding-auth-service-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --server.port=8093 \
  --auth.service.token=paicoding-auth-dev-token \
  --spring.cloud.nacos.discovery.enabled=false

start_service message-service /tmp/paicoding-skywalking/message \
  paicoding-message-service/target/paicoding-message-service-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --server.port=8095 \
  --spring.cloud.nacos.discovery.enabled=false

start_service forum-service /tmp/paicoding-skywalking/web \
  paicoding-web/target/paicoding-web-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --server.port=8081 \
  --spring.liquibase.enabled=false \
  --spring.cloud.nacos.discovery.enabled=false \
  --service.discovery.prefer-registry=false \
  --auth.service.mode=remote \
  --auth.service.token=paicoding-auth-dev-token \
  --message.service.mode=remote \
  --message.service.token=paicoding-message-dev-token

start_service paicoding-gateway /tmp/paicoding-skywalking/gateway \
  paicoding-gateway/target/paicoding-gateway-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --spring.cloud.nacos.discovery.enabled=false \
  --gateway.auth.internal-token=paicoding-auth-dev-token

wait_http() {
  local name="$1"
  local url="$2"
  local max_wait="${3:-90}"
  local code
  for ((i=1; i<=max_wait; i++)); do
    code="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' "${url}" 2>/dev/null || true)"
    if [[ "${code}" != "000" ]]; then
      echo "${name}=ready(http:${code})"
      return 0
    fi
    sleep 1
  done
  echo "${name}=timeout" >&2
  return 1
}

wait_http auth http://127.0.0.1:8093/actuator/health
wait_http message http://127.0.0.1:8095/actuator/health
wait_http web http://127.0.0.1:8081/actuator/health
wait_http gateway http://127.0.0.1:10010/actuator/health

echo '[gateway -> auth]'
curl --noproxy '*' -fsS \
  -H 'X-AUTH-INTERNAL-TOKEN: paicoding-auth-dev-token' \
  -H 'X-Trace-Id: skywalking-auth-proof-20260713' \
  http://127.0.0.1:10010/auth/internal/auth/config/probe
echo

echo '[gateway -> message]'
curl --noproxy '*' -fsS \
  -H 'X-MESSAGE-INTERNAL-TOKEN: paicoding-message-dev-token' \
  -H 'X-MESSAGE-USER-ID: 7' \
  -H 'X-Trace-Id: skywalking-message-proof-20260713' \
  http://127.0.0.1:10010/message/internal/message/notify/count
echo

echo '[gateway -> web -> auth login]'
login_response="$(curl --noproxy '*' -fsS -X POST \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: skywalking-full-chain-20260713' \
  -d '{"username":"pressure_user_1","password":"123456"}' \
  http://127.0.0.1:10010/new/login/username)"
echo "${login_response}"

token="$(printf '%s' "${login_response}" | node -e '
let raw="";
process.stdin.on("data", chunk => raw += chunk);
process.stdin.on("end", () => {
  const body = JSON.parse(raw);
  const value = body && body.result && body.result.token;
  if (!value) process.exit(2);
  process.stdout.write(value);
});')"

echo '[gateway -> web -> message notice query]'
curl --noproxy '*' -fsS \
  -H "Authorization: ${token}" \
  -H "x-access-token: ${token}" \
  -H 'X-Trace-Id: skywalking-full-chain-20260713' \
  'http://127.0.0.1:10010/notice/api/list?type=comment&page=1&pageSize=10'
echo

# The agent reports asynchronously and OAP writes traces in batches.
sleep 18

echo '[oap services]'
curl --noproxy '*' -fsS -X POST \
  -H 'Content-Type: application/json' \
  -d '{"query":"{listServices(layer: \"GENERAL\"){id name}}"}' \
  http://127.0.0.1:12800/graphql
echo

echo '[evidence completed]'
