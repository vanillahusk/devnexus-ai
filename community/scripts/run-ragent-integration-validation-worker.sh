#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/ragent-integration"
STATUS_FILE="${RUNTIME_DIR}/status"
PROGRESS_FILE="${RUNTIME_DIR}/progress.log"
RESULT_FILE="${RUNTIME_DIR}/result.log"
SUMMARY_FILE="${RUNTIME_DIR}/summary.env"
PID_FILE="${RUNTIME_DIR}/worker.pid"
CHILD_PID_FILE="${RUNTIME_DIR}/child.pid"
RAGENT_PID_FILE="${RUNTIME_DIR}/ragent.pid"
RAGENT_LOG_FILE="${RUNTIME_DIR}/ragent.log"
RAGENT_BUILD_LOG_FILE="${RUNTIME_DIR}/ragent-build.log"
TOTAL_TIMEOUT_SECONDS="${RAGENT_VALIDATION_TIMEOUT_SECONDS:-600}"
MIN_AVAILABLE_MEMORY_MB="${RAGENT_VALIDATION_MIN_AVAILABLE_MEMORY_MB:-900}"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent}"
RAGENT_JAR="${RAGENT_JAR:-${RAGENT_PROJECT_DIR}/bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar}"
RAGENT_BASE_URL="${RAGENT_BASE_URL:-http://127.0.0.1:9090/api/ragent}"
RAGENT_HOST="${RAGENT_HOST:-127.0.0.1}"
RAGENT_PORT="${RAGENT_PORT:-9090}"
RAGENT_ALLOW_DETERMINISTIC_EMBEDDING="${RAGENT_ALLOW_DETERMINISTIC_EMBEDDING:-true}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-16379}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
CHILD_PID=""
RAGENT_PID=""
RAGENT_OWNED=false
RAGENT_LIFECYCLE=NOT_STARTED
CANCELLED=false
START_EPOCH="$(date +%s)"
DEADLINE_EPOCH=$((START_EPOCH + TOTAL_TIMEOUT_SECONDS))

mkdir -p "${RUNTIME_DIR}"
existing_worker_pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
if [[ "${existing_worker_pid}" =~ ^[0-9]+$ ]] \
    && [[ "${existing_worker_pid}" != "$$" ]] \
    && kill -0 "${existing_worker_pid}" 2>/dev/null; then
  echo "Another Ragent validation worker is active, pid=${existing_worker_pid}" >&2
  exit 1
fi
rm -f "${STATUS_FILE}" "${PROGRESS_FILE}" "${RESULT_FILE}" "${SUMMARY_FILE}" \
  "${RAGENT_LOG_FILE}" "${RAGENT_BUILD_LOG_FILE}" "${CHILD_PID_FILE}" "${RAGENT_PID_FILE}"
printf '%s\n' "$$" >"${PID_FILE}"

write_status() {
  printf '%s\n' "$1" >"${STATUS_FILE}"
  printf '%s status=%s\n' "$(date -Iseconds)" "$1" >>"${PROGRESS_FILE}"
}

remaining_seconds() {
  local remaining=$((DEADLINE_EPOCH - $(date +%s)))
  if ((remaining <= 0)); then
    return 1
  fi
  printf '%s\n' "${remaining}"
}

stop_owned_process_group() {
  local pid="$1" label="$2"
  if [[ -z "${pid}" ]] || ! [[ "${pid}" =~ ^[0-9]+$ ]] || ! kill -0 "${pid}" 2>/dev/null; then
    return 0
  fi
  printf '%s cleanup=term label=%s pid=%s\n' "$(date -Iseconds)" "${label}" "${pid}" >>"${PROGRESS_FILE}"
  kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null || true
  for _ in {1..10}; do
    kill -0 "${pid}" 2>/dev/null || return 0
    sleep 1
  done
  printf '%s cleanup=kill label=%s pid=%s\n' "$(date -Iseconds)" "${label}" "${pid}" >>"${PROGRESS_FILE}"
  kill -KILL -- "-${pid}" 2>/dev/null || kill -KILL "${pid}" 2>/dev/null || true
}

finish() {
  local exit_code=$?
  local final_status end_epoch
  set +e
  stop_owned_process_group "${CHILD_PID}" validation-child
  if [[ "${RAGENT_OWNED}" == "true" ]]; then
    stop_owned_process_group "${RAGENT_PID}" ragent-service
  fi
  rm -f "${CHILD_PID_FILE}" "${RAGENT_PID_FILE}"
  end_epoch="$(date +%s)"
  if [[ "${CANCELLED}" == "true" ]]; then
    final_status="CANCELLED_CLEANED_UP"
  elif [[ "${exit_code}" -eq 0 ]]; then
    final_status="SUCCESS_CLEANED_UP"
  elif [[ "${exit_code}" -eq 124 ]]; then
    final_status="TIMED_OUT_CLEANED_UP"
  else
    final_status="FAILED_CLEANED_UP"
  fi
  {
    printf 'FINAL_STATUS=%s\n' "${final_status}"
    printf 'EXIT_CODE=%s\n' "${exit_code}"
    printf 'DURATION_SECONDS=%s\n' "$((end_epoch - START_EPOCH))"
    printf 'VALIDATION_SCOPE=%s\n' "Ragent adapter ONLINE_UPDATE_CHUNK_QUERY_OFFLINE"
    printf 'RAGENT_LIFECYCLE=%s\n' "${RAGENT_LIFECYCLE}"
  } >"${SUMMARY_FILE}"
  write_status "${final_status}"
  rm -f "${PID_FILE}"
}

cancel() {
  CANCELLED=true
  exit 143
}

wait_for_child() {
  local label="$1" exit_code
  set +e
  wait "${CHILD_PID}"
  exit_code=$?
  set -e
  CHILD_PID=""
  rm -f "${CHILD_PID_FILE}"
  if [[ "${exit_code}" -ne 0 ]]; then
    printf '%s phase=%s result=FAILED exit_code=%s\n' \
      "$(date -Iseconds)" "${label}" "${exit_code}" >>"${PROGRESS_FILE}"
    exit "${exit_code}"
  fi
}

ragent_api_ready() {
  local login_response token protected_response
  login_response="$(curl -sS --max-time 3 -H 'Content-Type: application/json' \
    -d "{\"username\":\"${RAGENT_USERNAME:-admin}\",\"password\":\"${RAGENT_PASSWORD:-admin}\"}" \
    "${RAGENT_BASE_URL}/auth/login" 2>/dev/null || true)"
  token="$(printf '%s' "${login_response}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  [[ -n "${token}" ]] || return 1
  protected_response="$(curl -sS --max-time 3 -H "Authorization: ${token}" \
    "${RAGENT_BASE_URL}/user/me" 2>/dev/null || true)"
  printf '%s' "${protected_response}" | rg -q '"code"[[:space:]]*:[[:space:]]*"0"'
}

ragent_jar_has_validation_embedding() {
  local infra_ai_jar="${RAGENT_PROJECT_DIR}/infra-ai/target/infra-ai-0.0.1-SNAPSHOT.jar"
  local outer_entries infra_entries
  [[ -s "${infra_ai_jar}" && ! "${infra_ai_jar}" -nt "${RAGENT_JAR}" ]] || return 1
  outer_entries="$(jar tf "${RAGENT_JAR}" 2>/dev/null)" || return 1
  [[ "${outer_entries}" == *'BOOT-INF/lib/infra-ai-0.0.1-SNAPSHOT.jar'* ]] || return 1
  infra_entries="$(jar tf "${infra_ai_jar}" 2>/dev/null)" || return 1
  [[ "${infra_entries}" == *'DeterministicValidationEmbeddingClient.class'* ]]
}

wait_for_ragent() {
  local consecutive=0
  while remaining_seconds >/dev/null; do
    if ! kill -0 "${RAGENT_PID}" 2>/dev/null; then
      echo "Managed Ragent exited before becoming ready." >>"${PROGRESS_FILE}"
      tail -n 120 "${RAGENT_LOG_FILE}" >>"${PROGRESS_FILE}" 2>/dev/null || true
      return 1
    fi
    if ragent_api_ready; then
      consecutive=$((consecutive + 1))
      if ((consecutive >= 3)); then
        sleep 5
        ragent_api_ready && return 0
        consecutive=0
      fi
    else
      consecutive=0
    fi
    sleep 2
  done
  return 124
}

trap finish EXIT
trap cancel INT TERM

write_status PREFLIGHT
if [[ -z "${SILICONFLOW_API_KEY:-}" ]]; then
  ollama_tags="$(curl -fsS --max-time 2 "${OLLAMA_BASE_URL:-http://127.0.0.1:11434}/api/tags" 2>/dev/null || true)"
  if printf '%s' "${ollama_tags}" | rg -q 'qwen3-embedding'; then
    export RAGENT_EMBEDDING_MODEL="${RAGENT_EMBEDDING_MODEL:-qwen-emb-local}"
  elif [[ "${RAGENT_ALLOW_DETERMINISTIC_EMBEDDING}" == "true" ]]; then
    export RAGENT_EMBEDDING_MODEL="${RAGENT_EMBEDDING_MODEL:-validation-embedding-1536}"
    export RAGENT_VALIDATION_EMBEDDING_ENABLED=true
    printf '%s embedding=DETERMINISTIC_VALIDATION_ONLY semantic_quality=NOT_VALIDATED\n' \
      "$(date -Iseconds)" >>"${PROGRESS_FILE}"
  fi
fi
if nc -z -w 1 "${RAGENT_HOST}" "${RAGENT_PORT}" >/dev/null 2>&1; then
  require_ragent=true
else
  require_ragent=false
fi
precheck_allow_deterministic="${RAGENT_ALLOW_DETERMINISTIC_EMBEDDING}"
if [[ "${require_ragent}" == "true" \
    && "${RAGENT_EMBEDDING_MODEL:-}" == "validation-embedding-1536" \
    && "${RAGENT_EXTERNAL_DETERMINISTIC_EMBEDDING_READY:-false}" != "true" ]]; then
  precheck_allow_deterministic=false
  printf '%s embedding=DETERMINISTIC_VALIDATION_ONLY external_ragent=UNVERIFIED\n' \
    "$(date -Iseconds)" >>"${PROGRESS_FILE}"
fi
if ! RAGENT_REQUIRE_SERVICE="${require_ragent}" MIN_AVAILABLE_MEMORY_MB="${MIN_AVAILABLE_MEMORY_MB}" \
    RAGENT_REQUIRE_MYSQL=false \
    RAGENT_ALLOW_DETERMINISTIC_EMBEDDING="${precheck_allow_deterministic}" \
    bash "${ROOT_DIR}/scripts/check-ragent-integration-dependencies.sh" >>"${PROGRESS_FILE}" 2>&1; then
  printf '%s preflight=FAILED downstream_java=NOT_STARTED\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
  exit 2
fi

if [[ "${require_ragent}" == "false" ]]; then
  ragent_build_required=false
  if [[ ! -s "${RAGENT_JAR}" ]]; then
    ragent_build_required=true
  elif [[ "${RAGENT_EMBEDDING_MODEL:-}" == "validation-embedding-1536" ]] \
      && ! ragent_jar_has_validation_embedding; then
    ragent_build_required=true
    printf '%s ragent_jar=STALE missing=deterministic-validation-embedding\n' \
      "$(date -Iseconds)" >>"${PROGRESS_FILE}"
  fi
  if [[ "${ragent_build_required}" == "true" ]]; then
    write_status BUILDING_RAGENT
    build_timeout="$(remaining_seconds)" || exit 124
    export RAGENT_PROJECT_DIR RAGENT_JAR
    setsid env \
      RAGENT_BUILD_TIMEOUT_SECONDS="${build_timeout}" \
      RAGENT_BUILD_LOG="${RAGENT_BUILD_LOG_FILE}" \
      bash "${ROOT_DIR}/scripts/build-ragent-integration-artifact.sh" >>"${PROGRESS_FILE}" 2>&1 &
    CHILD_PID=$!
    printf '%s\n' "${CHILD_PID}" >"${CHILD_PID_FILE}"
    wait_for_child ragent-build
  fi

  write_status STARTING_RAGENT
  export RAGENT_PROJECT_DIR RAGENT_JAR RAGENT_BASE_URL RAGENT_HOST RAGENT_PORT
  setsid bash "${ROOT_DIR}/scripts/run-ragent-low-resource.sh" >"${RAGENT_LOG_FILE}" 2>&1 &
  RAGENT_PID=$!
  RAGENT_OWNED=true
  RAGENT_LIFECYCLE=MANAGED
  printf '%s\n' "${RAGENT_PID}" >"${RAGENT_PID_FILE}"
  set +e
  wait_for_ragent
  ready_exit=$?
  set -e
  if [[ "${ready_exit}" -ne 0 ]]; then
    tail -n 120 "${RAGENT_LOG_FILE}" >>"${PROGRESS_FILE}" 2>/dev/null || true
    exit "${ready_exit}"
  fi
  printf '%s ragent=READY lifecycle=MANAGED\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
else
  RAGENT_LIFECYCLE=EXTERNAL
  printf '%s ragent=READY lifecycle=EXTERNAL\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
fi

write_status RUNNING
validation_timeout="$(remaining_seconds)" || exit 124
printf '%s validation=adapter remaining_timeout_seconds=%s\n' \
  "$(date -Iseconds)" "${validation_timeout}" >>"${PROGRESS_FILE}"

export RAGENT_REAL_INTEGRATION=true
export MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx320m -XX:+UseSerialGC}"

setsid timeout --signal=TERM --kill-after=20s "${validation_timeout}s" \
  mvn -pl paicoding-web -am \
    -Dtest=RagentKnowledgeSyncRealIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -DargLine=-Xmx320m \
    -Dlogging.level.root=WARN test >"${RESULT_FILE}" 2>&1 &
CHILD_PID=$!
printf '%s\n' "${CHILD_PID}" >"${CHILD_PID_FILE}"
wait_for_child adapter-validation

printf '%s validation=SUCCESS\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
