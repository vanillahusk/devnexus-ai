#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/article-knowledge-full-chain"
STATUS_FILE="${RUNTIME_DIR}/status"
PROGRESS_FILE="${RUNTIME_DIR}/progress.log"
RESULT_FILE="${RUNTIME_DIR}/result.log"
SUMMARY_FILE="${RUNTIME_DIR}/summary.env"
RAGENT_LOG="${RUNTIME_DIR}/ragent.log"
AIGC_LOG="${RUNTIME_DIR}/aigc.log"
PID_FILE="${RUNTIME_DIR}/worker.pid"
TOTAL_TIMEOUT_SECONDS="${ARTICLE_KNOWLEDGE_FULL_CHAIN_TIMEOUT_SECONDS:-900}"
ARTICLE_ID=9000000401
RAGENT_PID=""
AIGC_PID=""
INFRA_OWNED=false
START_EPOCH="$(date +%s)"
DEADLINE_EPOCH=$((START_EPOCH + TOTAL_TIMEOUT_SECONDS))

mkdir -p "${RUNTIME_DIR}"
existing_pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
if [[ "${existing_pid}" =~ ^[0-9]+$ ]] && kill -0 "${existing_pid}" 2>/dev/null; then
  echo "Full-chain worker is already active, pid=${existing_pid}" >&2
  exit 1
fi
rm -f "${STATUS_FILE}" "${PROGRESS_FILE}" "${RESULT_FILE}" "${SUMMARY_FILE}" \
  "${RAGENT_LOG}" "${AIGC_LOG}"
printf '%s\n' "$$" >"${PID_FILE}"

write_status() {
  printf '%s\n' "$1" >"${STATUS_FILE}"
  printf '%s status=%s\n' "$(date -Iseconds)" "$1" >>"${PROGRESS_FILE}"
}

remaining_seconds() {
  local remaining=$((DEADLINE_EPOCH - $(date +%s)))
  ((remaining > 0)) || return 1
  printf '%s\n' "${remaining}"
}

stop_process_group() {
  local pid="$1" label="$2"
  [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null || return 0
  printf '%s cleanup=term label=%s pid=%s\n' "$(date -Iseconds)" "${label}" "${pid}" >>"${PROGRESS_FILE}"
  kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null || true
  for _ in {1..15}; do
    kill -0 "${pid}" 2>/dev/null || return 0
    sleep 1
  done
  kill -KILL -- "-${pid}" 2>/dev/null || kill -KILL "${pid}" 2>/dev/null || true
}

cleanup_test_data() {
  MYSQL_PWD="${MYSQL_PASSWORD:-123456}" mysql -h"${MYSQL_HOST:-127.0.0.1}" \
    -P"${MYSQL_PORT:-3306}" -u"${MYSQL_USER:-root}" pai_coding -e \
    "DELETE FROM article_knowledge_index_state WHERE article_id=${ARTICLE_ID};
     DELETE FROM mq_outbox_event WHERE aggregate_id='article:${ARTICLE_ID}';
     DELETE FROM article_detail WHERE article_id=${ARTICLE_ID};
     DELETE FROM article WHERE id=${ARTICLE_ID};" >/dev/null 2>&1 || true
  redis-cli -h 127.0.0.1 -p 16379 -a 123456 HDEL \
    ai:knowledge:ragent:doc-mapping "article:${ARTICLE_ID}" >/dev/null 2>&1 || true
  redis-cli -h 127.0.0.1 -p 16379 -a 123456 HDEL \
    ai:knowledge:ragent:sync-status "article:${ARTICLE_ID}" >/dev/null 2>&1 || true
}

finish() {
  local exit_code=$? final_status
  set +e
  stop_process_group "${AIGC_PID}" aigc-service
  stop_process_group "${RAGENT_PID}" ragent-service
  cleanup_test_data
  if [[ "${INFRA_OWNED}" == "true" ]]; then
    bash "${ROOT_DIR}/scripts/stop-ragent-low-resource-dependencies.sh" >>"${PROGRESS_FILE}" 2>&1
  fi
  if [[ "${exit_code}" -eq 0 ]]; then final_status=SUCCESS_CLEANED_UP;
  elif [[ "${exit_code}" -eq 124 ]]; then final_status=TIMED_OUT_CLEANED_UP;
  else final_status=FAILED_CLEANED_UP; fi
  {
    printf 'FINAL_STATUS=%s\n' "${final_status}"
    printf 'EXIT_CODE=%s\n' "${exit_code}"
    printf 'DURATION_SECONDS=%s\n' "$(( $(date +%s) - START_EPOCH ))"
    printf 'VALIDATION_SCOPE=%s\n' 'ARTICLE_OUTBOX_ROCKETMQ_AIGC_RAGENT_STATE'
  } >"${SUMMARY_FILE}"
  write_status "${final_status}"
  bash "${ROOT_DIR}/scripts/generate-article-knowledge-full-chain-report.sh" \
    >>"${PROGRESS_FILE}" 2>&1 || true
  rm -f "${PID_FILE}"
}
trap finish EXIT

wait_http_200() {
  local url="$1" pid="$2" consecutive=0
  while remaining_seconds >/dev/null; do
    kill -0 "${pid}" 2>/dev/null || return 1
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "${url}" 2>/dev/null || true)"
    if [[ "${code}" == 200 ]]; then
      consecutive=$((consecutive + 1))
      ((consecutive >= 3)) && return 0
    else
      consecutive=0
    fi
    sleep 2
  done
  return 124
}

ragent_token() {
  local login token
  login="$(curl -sS --max-time 3 -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    http://127.0.0.1:9090/api/ragent/auth/login 2>/dev/null || true)"
  token="$(printf '%s' "${login}" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  [[ -n "${token}" ]] || return 1
  printf '%s' "${token}"
}

ragent_ready() {
  local token me
  token="$(ragent_token)" || return 1
  me="$(curl -sS --max-time 3 -H "Authorization: ${token}" \
    http://127.0.0.1:9090/api/ragent/user/me 2>/dev/null || true)"
  printf '%s' "${me}" | rg -q '"code"[[:space:]]*:[[:space:]]*"0"'
}

wait_ragent() {
  local consecutive=0
  while remaining_seconds >/dev/null; do
    kill -0 "${RAGENT_PID}" 2>/dev/null || return 1
    if ragent_ready; then consecutive=$((consecutive + 1)); else consecutive=0; fi
    if ((consecutive >= 3)); then sleep 5; ragent_ready && return 0; consecutive=0; fi
    sleep 2
  done
  return 124
}

write_status HOST_PREFLIGHT
MYSQL_PWD="${MYSQL_PASSWORD:-123456}" mysql -h"${MYSQL_HOST:-127.0.0.1}" \
  -P"${MYSQL_PORT:-3306}" -u"${MYSQL_USER:-root}" pai_coding -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='pai_coding' AND table_name IN ('article','article_detail','mq_outbox_event','article_knowledge_index_state');" \
  | rg -qx '4'
[[ -s "${ROOT_DIR}/paicoding-aigc-service/target/paicoding-aigc-service-0.0.1-SNAPSHOT.jar" ]]

write_status STARTING_INFRA
bash "${ROOT_DIR}/scripts/start-ragent-low-resource-dependencies.sh" >>"${PROGRESS_FILE}" 2>&1
INFRA_OWNED=true

write_status DEPENDENCY_PREFLIGHT
RAGENT_REQUIRE_MYSQL=true RAGENT_REQUIRE_SERVICE=false \
  RAGENT_ALLOW_DETERMINISTIC_EMBEDDING=true MIN_AVAILABLE_MEMORY_MB=1100 \
  bash "${ROOT_DIR}/scripts/check-ragent-integration-dependencies.sh" >>"${PROGRESS_FILE}" 2>&1

write_status STARTING_RAGENT
export REDIS_HOST=127.0.0.1 REDIS_PORT=16379 REDIS_PASSWORD=123456
export RAGENT_EMBEDDING_MODEL=validation-embedding-1536 RAGENT_VALIDATION_EMBEDDING_ENABLED=true
export RAGENT_BAILIAN_RERANK_ENABLED=false
setsid bash "${ROOT_DIR}/scripts/run-ragent-low-resource.sh" >"${RAGENT_LOG}" 2>&1 &
RAGENT_PID=$!
wait_ragent
printf '%s ragent=READY\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"

write_status STARTING_AIGC
setsid bash "${ROOT_DIR}/scripts/run-aigc-knowledge-low-resource.sh" >"${AIGC_LOG}" 2>&1 &
AIGC_PID=$!
wait_http_200 http://127.0.0.1:8094/actuator/health "${AIGC_PID}"
sleep 5
printf '%s aigc=READY\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"

write_status RUNNING
validation_timeout="$(remaining_seconds)" || exit 124
MAVEN_OPTS='-Xms128m -Xmx320m -XX:+UseSerialGC' timeout --signal=TERM --kill-after=20s \
  "${validation_timeout}s" mvn -o -pl paicoding-web -am \
  -Dtest=ArticleKnowledgeFullChainRealIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-Xmx320m \
  -Darticle.knowledge.full-chain.integration.enabled=true \
  -Dspring.data.redis.host=127.0.0.1 -Dspring.data.redis.port=16379 \
  -Dspring.data.redis.password=123456 test >"${RESULT_FILE}" 2>&1
rg -q 'result=DUPLICATE' "${AIGC_LOG}"
rg -q 'result=STALE' "${AIGC_LOG}"
rg -q 'version=4, result=FAILED' "${AIGC_LOG}"
rg -q 'version=4, result=APPLIED' "${AIGC_LOG}"
printf '%s duplicate_event=VERIFIED stale_event=VERIFIED failure_recovery=VERIFIED\n' \
  "$(date -Iseconds)" >>"${PROGRESS_FILE}"

write_status VERIFYING_VECTOR_METADATA
vector_metadata_rows="$(docker exec paicoding-ragent-postgres psql -U postgres -d ragent \
  -tA -F '|' -c \
  "SELECT id,
          metadata->>'articleId',
          metadata->>'articleVersion',
          metadata->>'sourceType',
          metadata->>'status',
          metadata->>'headingPath',
          metadata->>'contentHash',
          metadata->>'tokenCount',
          metadata->>'overlapTokenCount'
     FROM t_knowledge_vector
    WHERE metadata->>'articleId'='${ARTICLE_ID}'
      AND metadata->>'articleVersion'='4'
      AND metadata->>'sourceType'='ARTICLE'
      AND metadata->>'status'='ONLINE'
      AND COALESCE(metadata->>'headingPath', '') <> ''
      AND metadata->>'contentHash' ~ '^[0-9a-f]{64}$'
      AND metadata->>'tokenCount' ~ '^[0-9]+$'
      AND metadata->>'overlapTokenCount' ~ '^[0-9]+$'
      AND id ~ '^[0-9a-f]{20}$'
    ORDER BY metadata->>'chunk_index';")"
[[ -n "${vector_metadata_rows}" ]]
vector_metadata_count="$(printf '%s\n' "${vector_metadata_rows}" | sed '/^[[:space:]]*$/d' | wc -l)"
printf '%s vector_metadata=VERIFIED stable_chunk_id=VERIFIED token_metadata=VERIFIED vector_rows=%s\n' \
  "$(date -Iseconds)" "${vector_metadata_count}" >>"${PROGRESS_FILE}"
printf '%s\n' "${vector_metadata_rows}" | sed -E 's/\|[0-9a-f]{64}\|/|<sha256>|/' \
  | sed 's/^/VECTOR_METADATA_EVIDENCE=/' >>"${RESULT_FILE}"

write_status VERIFYING_HYBRID_RETRIEVAL
ragent_auth_token="$(ragent_token)"
hybrid_response="$(curl -sS --fail-with-body --max-time 20 \
  -H "Authorization: ${ragent_auth_token}" -H 'Content-Type: application/json' \
  -d "{\"query\":\"full-chain-recovered\",\"topK\":5,\"collectionName\":\"paicodingcommunitykb\",\"metadataFilters\":{\"sourceType\":\"ARTICLE\",\"status\":\"ONLINE\",\"articleId\":\"${ARTICLE_ID}\",\"articleVersion\":\"4\"}}" \
  http://127.0.0.1:9090/api/ragent/rag/retrieve/hybrid)"
printf '%s' "${hybrid_response}" | rg -q '"code"[[:space:]]*:[[:space:]]*"0"'
printf '%s' "${hybrid_response}" | rg -q '"id"[[:space:]]*:'
printf '%s' "${hybrid_response}" | rg -q '"articleId"[[:space:]]*:[[:space:]]*9000000401'
printf '%s' "${hybrid_response}" | rg -q '"articleVersion"[[:space:]]*:[[:space:]]*4'
printf '%s' "${hybrid_response}" | rg -q '"status"[[:space:]]*:[[:space:]]*"ONLINE"'
printf '%s' "${hybrid_response}" | rg -q '"headingPath"[[:space:]]*:[[:space:]]*"full-chain-recovered"'
printf '%s hybrid_retrieval=VERIFIED metadata_filter=VERIFIED rrf=EXECUTED semantic_quality=NOT_VALIDATED\n' \
  "$(date -Iseconds)" >>"${PROGRESS_FILE}"

write_status VERIFYING_TRUSTED_RETRIEVAL
trusted_response="$(curl -sS --fail-with-body --max-time 20 \
  -H "Authorization: ${ragent_auth_token}" -H 'Content-Type: application/json' \
  -d "{\"query\":\"full-chain-recovered\",\"candidateTopK\":20,\"topK\":6,\"maxContextTokens\":4000,\"collectionName\":\"paicodingcommunitykb\",\"metadataFilters\":{\"articleId\":\"${ARTICLE_ID}\",\"articleVersion\":\"4\"}}" \
  http://127.0.0.1:9090/api/ragent/rag/retrieve/trusted)"
printf '%s' "${trusted_response}" | rg -q '"code"[[:space:]]*:[[:space:]]*"0"'
printf '%s' "${trusted_response}" | rg -q '"answerable"[[:space:]]*:[[:space:]]*true'
printf '%s' "${trusted_response}" | rg -q '"decisionCode"[[:space:]]*:[[:space:]]*"EXACT_IDENTIFIER_EVIDENCE"'
printf '%s' "${trusted_response}" | rg -q '"context"[[:space:]]*:[[:space:]]*"<untrusted_documents>'
printf '%s' "${trusted_response}" | rg -q '"articleId"[[:space:]]*:[[:space:]]*"9000000401"'
printf '%s' "${trusted_response}" | rg -q '"title"[[:space:]]*:[[:space:]]*"[^"].*"'
printf '%s' "${trusted_response}" | rg -q '"headingPath"[[:space:]]*:[[:space:]]*"full-chain-recovered"'
printf '%s trusted_retrieval=VERIFIED token_budget=VERIFIED citation_traceability=VERIFIED evidence_policy=VERIFIED rerank=NOOP_VALIDATION_ONLY\n' \
  "$(date -Iseconds)" >>"${PROGRESS_FILE}"

printf '%s validation=SUCCESS\n' "$(date -Iseconds)" >>"${PROGRESS_FILE}"
