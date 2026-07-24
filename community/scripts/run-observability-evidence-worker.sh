#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/observability"
STATUS_FILE="${RUNTIME_DIR}/status"
PROGRESS_FILE="${RUNTIME_DIR}/progress.log"
RESULT_FILE="${RUNTIME_DIR}/result.log"
ARTIFACT_DIR="${RUNTIME_DIR}/artifacts"
REPORT_FILE="${ROOT_DIR}/docs/perf/可观测性与SkyWalking验证报告.md"
SERVICE_CONTAINERS=(
  paicoding-evidence-auth
  paicoding-evidence-message
  paicoding-evidence-web
  paicoding-evidence-gateway
)
OBSERVABILITY_CONTAINERS=(
  paicoding-prometheus
  paicoding-grafana
  paicoding-skywalking-ui
  paicoding-skywalking-oap
  paicoding-skywalking-banyandb
)

mkdir -p "${RUNTIME_DIR}" "${ARTIFACT_DIR}"
rm -f "${RUNTIME_DIR}"/report.*

write_status() {
  printf '%s\n' "$1" >"${STATUS_FILE}"
  printf '%s status=%s\n' "$(date -Iseconds)" "$1" >>"${PROGRESS_FILE}"
}

cleanup() {
  local exit_code=$?
  local final_status
  local cleanup_result
  set +e
  printf '%s cleanup=started exit_code=%s\n' "$(date -Iseconds)" "${exit_code}" >>"${PROGRESS_FILE}"
  cleanup_result=SUCCESS
  for container_name in "${SERVICE_CONTAINERS[@]}" "${OBSERVABILITY_CONTAINERS[@]}"; do
    if docker inspect "${container_name}" >/dev/null 2>&1; then
      docker stop "${container_name}" >>"${PROGRESS_FILE}" 2>&1 \
        || cleanup_result=PARTIAL_FAILURE
    fi
  done
  printf '%s cleanup=finished result=%s\n' \
    "$(date -Iseconds)" "${cleanup_result}" >>"${PROGRESS_FILE}"
  if [[ "${exit_code}" -eq 0 && "${cleanup_result}" == "SUCCESS" ]]; then
    final_status=SUCCESS_CLEANED_UP
  elif [[ "${exit_code}" -eq 0 ]]; then
    final_status=SUCCESS_CLEANUP_FAILED
  elif [[ "${cleanup_result}" == "SUCCESS" ]]; then
    final_status=FAILED_CLEANED_UP
  else
    final_status=FAILED_CLEANUP_FAILED
  fi
  write_status "${final_status}"
  EVIDENCE_FINAL_STATUS="${final_status}" EVIDENCE_EXIT_CODE="${exit_code}" \
    bash "${ROOT_DIR}/scripts/generate-observability-report.sh" \
    >>"${PROGRESS_FILE}" 2>&1
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_http_200() {
  local service_name="$1"
  local url="$2"
  local max_wait="${3:-180}"
  local code
  for ((i=1; i<=max_wait; i++)); do
    code="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' "${url}" 2>/dev/null || true)"
    if [[ "${code}" == "200" ]]; then
      printf '%s service=%s ready_http=%s seconds=%s\n' \
        "$(date -Iseconds)" "${service_name}" "${code}" "${i}" >>"${PROGRESS_FILE}"
      return 0
    fi
    if ! docker inspect "${service_name}" --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
      docker logs --tail 100 "${service_name}" >>"${PROGRESS_FILE}" 2>&1
      return 1
    fi
    sleep 1
  done
  printf '%s service=%s timeout=%s\n' "$(date -Iseconds)" "${service_name}" "${max_wait}" >>"${PROGRESS_FILE}"
  docker logs --tail 100 "${service_name}" >>"${PROGRESS_FILE}" 2>&1
  return 1
}

wait_oap_stable() {
  local url="http://127.0.0.1:12800/healthcheck"
  local consecutive_required="${OAP_CONSECUTIVE_SUCCESSES:-3}"
  local stability_wait="${OAP_STABILITY_WAIT_SECONDS:-10}"
  local consecutive=0
  local code

  for ((i=1; i<=240; i++)); do
    code="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' "${url}" 2>/dev/null || true)"
    if [[ "${code}" == "200" ]]; then
      consecutive=$((consecutive + 1))
      if ((consecutive >= consecutive_required)); then
        printf '%s service=oap consecutive_200=%s stability_wait=%s\n' \
          "$(date -Iseconds)" "${consecutive}" "${stability_wait}" >>"${PROGRESS_FILE}"
        sleep "${stability_wait}"
        code="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' "${url}" 2>/dev/null || true)"
        if [[ "${code}" == "200" ]]; then
          printf '%s service=oap stable_http=200\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
          return 0
        fi
        consecutive=0
      fi
    else
      consecutive=0
    fi
    if ! docker inspect paicoding-skywalking-oap --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
      docker logs --tail 120 paicoding-skywalking-oap >>"${PROGRESS_FILE}" 2>&1
      return 1
    fi
    sleep 1
  done

  printf '%s service=oap stable_http_200_timeout=240\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
  docker logs --tail 120 paicoding-skywalking-oap >>"${PROGRESS_FILE}" 2>&1
  return 1
}

check_tcp() {
  local dependency="$1"
  local host="$2"
  local port="$3"
  if timeout 3 bash -c "exec 3<>/dev/tcp/${host}/${port}" 2>/dev/null; then
    printf '%s dependency=%s address=%s:%s status=UP\n' \
      "$(date -Iseconds)" "${dependency}" "${host}" "${port}" >>"${PROGRESS_FILE}"
    return 0
  fi
  printf '%s dependency=%s address=%s:%s status=DOWN\n' \
    "$(date -Iseconds)" "${dependency}" "${host}" "${port}" >>"${PROGRESS_FILE}"
  return 1
}

preflight_dependencies() {
  local failures=0
  check_tcp mysql "${EVIDENCE_MYSQL_HOST:-127.0.0.1}" "${EVIDENCE_MYSQL_PORT:-3306}" || failures=$((failures + 1))
  check_tcp redis "${EVIDENCE_REDIS_HOST:-127.0.0.1}" "${EVIDENCE_REDIS_PORT:-6379}" || failures=$((failures + 1))
  check_tcp rocketmq-nameserver "${EVIDENCE_ROCKETMQ_HOST:-127.0.0.1}" "${EVIDENCE_ROCKETMQ_PORT:-9876}" || failures=$((failures + 1))
  check_tcp skywalking-oap-http "${EVIDENCE_OAP_HOST:-127.0.0.1}" "${EVIDENCE_OAP_HTTP_PORT:-12800}" || failures=$((failures + 1))
  check_tcp skywalking-oap-grpc "${EVIDENCE_OAP_HOST:-127.0.0.1}" "${EVIDENCE_OAP_GRPC_PORT:-11800}" || failures=$((failures + 1))
  if ((failures > 0)); then
    printf '%s preflight=FAILED missing_dependencies=%s\n' \
      "$(date -Iseconds)" "${failures}" >>"${PROGRESS_FILE}"
    return 1
  fi
  printf '%s preflight=PASSED\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
}

start_and_wait() {
  local container_name="$1"
  local health_url="$2"
  docker start "${container_name}" >>"${PROGRESS_FILE}"
  wait_http_200 "${container_name}" "${health_url}"
}

write_status STARTING
wait_oap_stable

write_status PREFLIGHT
preflight_dependencies

write_status WAITING_SERVICES
start_and_wait paicoding-evidence-auth http://127.0.0.1:8093/actuator/prometheus
start_and_wait paicoding-evidence-message http://127.0.0.1:8095/actuator/prometheus
start_and_wait paicoding-evidence-web http://127.0.0.1:8081/actuator/prometheus
start_and_wait paicoding-evidence-gateway http://127.0.0.1:10010/actuator/prometheus

write_status COLLECTING
OBSERVABILITY_ARTIFACT_DIR="${ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/scripts/collect-skywalking-evidence.sh" >"${RESULT_FILE}" 2>&1

write_status SUCCESS
