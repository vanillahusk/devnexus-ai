#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_DIR="${OBSERVABILITY_ARTIFACT_DIR:-${ROOT_DIR}/.runtime/observability/artifacts}"
RUN_ID="$(date -u +%Y%m%d%H%M%S)"
AUTH_TOKEN_HEADER='X-AUTH-INTERNAL-TOKEN: paicoding-auth-dev-token'
MESSAGE_TOKEN_HEADER='X-MESSAGE-INTERNAL-TOKEN: paicoding-message-dev-token'

mkdir -p "${ARTIFACT_DIR}"

assert_contains() {
  local file="$1"
  local pattern="$2"
  local description="$3"
  if ! grep -Eq "${pattern}" "${file}"; then
    echo "evidence assertion failed: ${description}, file=${file}" >&2
    return 1
  fi
}

response_trace_id() {
  local headers_file="$1"
  sed -n 's/^[Xx]-[Tt]race-[Ii]d:[[:space:]]*\(.*\)\r$/\1/p' "${headers_file}" | tail -n 1
}

graphql_query() {
  local payload="$1"
  local output_file="$2"
  local http_code
  http_code="$(curl --noproxy '*' -sS -X POST \
    -H 'Content-Type: application/json' \
    -d "${payload}" \
    -o "${output_file}" \
    -w '%{http_code}' \
    http://127.0.0.1:12800/graphql)"
  if [[ "${http_code}" != "200" ]]; then
    echo "SkyWalking GraphQL HTTP ${http_code}: ${output_file}" >&2
    sed -n '1,80p' "${output_file}" >&2
    return 1
  fi
  if grep -q '"errors"' "${output_file}"; then
    echo "SkyWalking GraphQL returned errors: ${output_file}" >&2
    return 1
  fi
}

graphql_document() {
  local document="$1"
  local output_file="$2"
  local escaped_document
  escaped_document="$(printf '%s' "${document}" \
    | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
  graphql_query "{\"query\":\"${escaped_document}\"}" "${output_file}"
}

echo '[normal gateway -> auth]'
curl --noproxy '*' -fsS \
  -D "${ARTIFACT_DIR}/normal-auth.headers" \
  -o "${ARTIFACT_DIR}/normal-auth.json" \
  -H "${AUTH_TOKEN_HEADER}" \
  -H "X-Trace-Id: normal-auth-${RUN_ID}" \
  http://127.0.0.1:10010/auth/internal/auth/config/probe
assert_contains "${ARTIFACT_DIR}/normal-auth.json" '"code"[[:space:]]*:[[:space:]]*0' 'normal auth call'

echo '[normal gateway -> message]'
curl --noproxy '*' -fsS \
  -D "${ARTIFACT_DIR}/normal-message.headers" \
  -o "${ARTIFACT_DIR}/normal-message.json" \
  -H "${MESSAGE_TOKEN_HEADER}" \
  -H 'X-MESSAGE-USER-ID: 7' \
  -H "X-Trace-Id: normal-message-${RUN_ID}" \
  http://127.0.0.1:10010/message/internal/message/notify/count
assert_contains "${ARTIFACT_DIR}/normal-message.json" '"code"[[:space:]]*:[[:space:]]*0' 'normal message call'

echo '[slow gateway -> auth: 1500ms]'
slow_code="$(curl --noproxy '*' -sS \
  -D "${ARTIFACT_DIR}/slow.headers" \
  -o "${ARTIFACT_DIR}/slow.json" \
  -w '%{http_code}' \
  -H "${AUTH_TOKEN_HEADER}" \
  -H "X-Trace-Id: slow-proof-${RUN_ID}" \
  'http://127.0.0.1:10010/auth/internal/auth/observability/probe?delayMs=1500')"
[[ "${slow_code}" == "200" ]]
assert_contains "${ARTIFACT_DIR}/slow.json" 'delayMs=1500' 'controlled slow call'

echo '[error gateway -> auth: intentional 5xx]'
error_code="$(curl --noproxy '*' -sS \
  -D "${ARTIFACT_DIR}/error.headers" \
  -o "${ARTIFACT_DIR}/error.json" \
  -w '%{http_code}' \
  -H "${AUTH_TOKEN_HEADER}" \
  -H "X-Trace-Id: error-proof-${RUN_ID}" \
  'http://127.0.0.1:10010/auth/internal/auth/observability/probe?fail=true')"
[[ "${error_code}" =~ ^5[0-9][0-9]$ ]]

echo '[gateway -> web -> auth login]'
login_code="$(curl --noproxy '*' -sS -X POST \
  -D "${ARTIFACT_DIR}/login.headers" \
  -o "${ARTIFACT_DIR}/login.private.json" \
  -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -H "X-Trace-Id: full-chain-${RUN_ID}" \
  -d '{"username":"pressure_user_1","password":"123456"}' \
  http://127.0.0.1:10010/new/login/username)"
chmod 0600 "${ARTIFACT_DIR}/login.private.json"

token="$(sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
  "${ARTIFACT_DIR}/login.private.json")"
if [[ "${login_code}" != "200" || -z "${token}" ]]; then
  echo '[register deterministic evidence user]'
  register_code="$(curl --noproxy '*' -sS -X POST \
    -o "${ARTIFACT_DIR}/register.json" \
    -w '%{http_code}' \
    -H "X-Trace-Id: register-proof-${RUN_ID}" \
    -d 'username=pressure_user_1&password=123456' \
    http://127.0.0.1:10010/login/register)"
  [[ "${register_code}" == "200" ]]

  login_code="$(curl --noproxy '*' -sS -X POST \
    -D "${ARTIFACT_DIR}/login.headers" \
    -o "${ARTIFACT_DIR}/login.private.json" \
    -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -H "X-Trace-Id: full-chain-${RUN_ID}" \
    -d '{"username":"pressure_user_1","password":"123456"}' \
    http://127.0.0.1:10010/new/login/username)"
  chmod 0600 "${ARTIFACT_DIR}/login.private.json"
  token="$(sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    "${ARTIFACT_DIR}/login.private.json")"
fi
[[ "${login_code}" == "200" ]]
test -n "${token}"
printf 'login=success token_length=%s\n' "${#token}"

echo '[gateway -> web -> message notice query]'
curl --noproxy '*' -fsS \
  -D "${ARTIFACT_DIR}/notice.headers" \
  -o "${ARTIFACT_DIR}/notice.json" \
  -H "Authorization: ${token}" \
  -H "x-access-token: ${token}" \
  -H "X-Trace-Id: full-chain-${RUN_ID}" \
  'http://127.0.0.1:10010/notice/api/list?type=comment&page=1&pageSize=10'
assert_contains "${ARTIFACT_DIR}/notice.json" '"code"[[:space:]]*:[[:space:]]*0' 'remote notice query'

echo '[gateway -> web -> RocketMQ real comment event]'
comment_content="skywalking-evidence-${RUN_ID}"
curl --noproxy '*' -fsS -X POST \
  -D "${ARTIFACT_DIR}/comment.headers" \
  -o "${ARTIFACT_DIR}/comment.json" \
  -H 'Content-Type: application/json' \
  -H "Authorization: ${token}" \
  -H "x-access-token: ${token}" \
  -H "X-Trace-Id: rocketmq-proof-${RUN_ID}" \
  -d "{\"articleId\":14,\"commentContent\":\"${comment_content}\",\"parentCommentId\":0,\"topCommentId\":0}" \
  http://127.0.0.1:10010/comment/api/save
assert_contains "${ARTIFACT_DIR}/comment.json" '"code"[[:space:]]*:[[:space:]]*0' 'real RocketMQ comment request'

{
  printf 'normal_auth=%s\n' "$(response_trace_id "${ARTIFACT_DIR}/normal-auth.headers")"
  printf 'normal_message=%s\n' "$(response_trace_id "${ARTIFACT_DIR}/normal-message.headers")"
  printf 'slow=%s\n' "$(response_trace_id "${ARTIFACT_DIR}/slow.headers")"
  printf 'error=%s\n' "$(response_trace_id "${ARTIFACT_DIR}/error.headers")"
  printf 'full_chain=%s\n' "$(response_trace_id "${ARTIFACT_DIR}/notice.headers")"
  printf 'rocketmq=%s\n' "$(response_trace_id "${ARTIFACT_DIR}/comment.headers")"
} >"${ARTIFACT_DIR}/business-trace-ids.txt"

# Prometheus now scrapes every 15 seconds. Wait for at least one complete cycle.
sleep 20

echo '[prometheus online targets]'
curl --noproxy '*' -fsSG \
  --data-urlencode 'query=up{job=~"paicoding-.*"}' \
  http://127.0.0.1:9090/api/v1/query >"${ARTIFACT_DIR}/prometheus-targets.json"
assert_contains "${ARTIFACT_DIR}/prometheus-targets.json" '"job":"paicoding-web"' 'Prometheus web target'
assert_contains "${ARTIFACT_DIR}/prometheus-targets.json" '"job":"paicoding-gateway"' 'Prometheus gateway target'
assert_contains "${ARTIFACT_DIR}/prometheus-targets.json" '"job":"paicoding-auth-service"' 'Prometheus auth target'
assert_contains "${ARTIFACT_DIR}/prometheus-targets.json" '"job":"paicoding-message-service"' 'Prometheus message target'

echo '[prometheus JVM / executor / connection metrics]'
curl --noproxy '*' -fsSG \
  --data-urlencode 'query=jvm_threads_live_threads or executor_active_threads or hikaricp_connections_active or jdbc_connections_active' \
  http://127.0.0.1:9090/api/v1/query >"${ARTIFACT_DIR}/prometheus-runtime-metrics.json"
assert_contains "${ARTIFACT_DIR}/prometheus-runtime-metrics.json" 'jvm_threads_live_threads' 'JVM thread metric'
assert_contains "${ARTIFACT_DIR}/prometheus-runtime-metrics.json" 'hikaricp_connections_active|jdbc_connections_active' 'database connection metric'

# Agent reporting and BanyanDB persistence are asynchronous.
sleep 30

query_start="$(date -u -d '-15 minutes' '+%Y-%m-%d %H%M')"
query_end="$(date -u -d '+2 minutes' '+%Y-%m-%d %H%M')"

echo '[SkyWalking services]'
services_query='{listServices(layer: "GENERAL"){id name}}'
graphql_document "${services_query}" "${ARTIFACT_DIR}/oap-services.json"
for service in paicoding-gateway forum-service auth-service message-service; do
  assert_contains "${ARTIFACT_DIR}/oap-services.json" "${service}" "SkyWalking service ${service}"
done

echo '[SkyWalking traces]'
printf -v traces_query \
  '{queryTraces(condition:{queryDuration:{start:"%s",end:"%s",step:MINUTE},traceState:ALL,queryOrder:BY_DURATION,paging:{pageNum:1,pageSize:100}}){traces{spans{traceId segmentId spanId parentSpanId serviceCode endpointName type peer component isError layer tags{key value}}} retrievedTimeRange{startTime endTime}}}' \
  "${query_start}" "${query_end}"
graphql_document "${traces_query}" "${ARTIFACT_DIR}/oap-traces.json"
assert_contains "${ARTIFACT_DIR}/oap-traces.json" 'observability/probe' 'slow/error probe traces'
assert_contains "${ARTIFACT_DIR}/oap-traces.json" '"isError"[[:space:]]*:[[:space:]]*true' 'error trace'

echo '[SkyWalking trace span details]'
cp "${ARTIFACT_DIR}/oap-traces.json" "${ARTIFACT_DIR}/oap-trace-details.jsonl"

echo '[SkyWalking topology]'
printf -v topology_query \
  '{getGlobalTopology(duration:{start:"%s",end:"%s",step:MINUTE}){nodes{id name type isReal} calls{source target id detectPoints}}}' \
  "${query_start}" "${query_end}"
graphql_document "${topology_query}" "${ARTIFACT_DIR}/oap-topology.json"
for service in paicoding-gateway forum-service auth-service message-service; do
  assert_contains "${ARTIFACT_DIR}/oap-topology.json" "${service}" "topology node ${service}"
done
if grep -Eq '"calls"[[:space:]]*:[[:space:]]*\[\]' "${ARTIFACT_DIR}/oap-topology.json"; then
  echo 'SkyWalking topology contains no calls' >&2
  exit 1
fi

cat "${ARTIFACT_DIR}/oap-trace-details.jsonl" "${ARTIFACT_DIR}/oap-topology.json" \
  >"${ARTIFACT_DIR}/oap-async-search.txt"
assert_contains "${ARTIFACT_DIR}/oap-async-search.txt" \
  'RocketMQ|rocketmq|paicoding-business-event' \
  'RocketMQ producer/consumer asynchronous segment'

printf 'run_id=%s\n' "${RUN_ID}"
printf 'slow_http=%s error_http=%s\n' "${slow_code}" "${error_code}"
printf 'comment_content=%s\n' "${comment_content}"
printf 'query_window=%s..%s\n' "${query_start}" "${query_end}"
echo 'evidence_collection=SUCCESS'
