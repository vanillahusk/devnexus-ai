#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
RUNTIME_DIR="${ROOT_DIR}/.runtime/rag-fault-regression"
RAGENT_LOG="${RUNTIME_DIR}/ragent-tests.log"
PAICODING_LOG="${RUNTIME_DIR}/paicoding-web-tests.log"
AIGC_LOG="${RUNTIME_DIR}/paicoding-aigc-tests.log"
STATIC_LOG="${RUNTIME_DIR}/static-checks.log"
SUMMARY_FILE="${RUNTIME_DIR}/summary.env"
TOTAL_TIMEOUT_SECONDS="${RAG_FAULT_REGRESSION_TIMEOUT_SECONDS:-420}"
START_EPOCH="$(date +%s)"
DEADLINE_EPOCH=$((START_EPOCH + TOTAL_TIMEOUT_SECONDS))

mkdir -p "${RUNTIME_DIR}"
rm -f "${RAGENT_LOG}" "${PAICODING_LOG}" "${AIGC_LOG}" "${STATIC_LOG}" "${SUMMARY_FILE}"

remaining_seconds() {
  local remaining=$((DEADLINE_EPOCH - $(date +%s)))
  ((remaining > 0)) || return 1
  printf '%s\n' "${remaining}"
}

finish() {
  local exit_code=$? final_status
  if [[ "${exit_code}" -eq 0 ]]; then
    final_status=SUCCESS
  elif [[ "${exit_code}" -eq 124 ]]; then
    final_status=TIMED_OUT
  else
    final_status=FAILED
  fi
  {
    printf 'FINAL_STATUS=%s\n' "${final_status}"
    printf 'EXIT_CODE=%s\n' "${exit_code}"
    printf 'DURATION_SECONDS=%s\n' "$(( $(date +%s) - START_EPOCH ))"
    printf 'SERVICES_STARTED=NO\n'
    printf 'EVIDENCE_LEVEL=L1_L2\n'
  } >"${SUMMARY_FILE}"
}
trap finish EXIT

timeout_seconds="$(remaining_seconds)"
timeout --signal=TERM --kill-after=15s "${timeout_seconds}s" \
  mvn -o -f "${RAGENT_PROJECT_DIR}/pom.xml" -pl bootstrap -am \
  -Dtest=EmbeddingCallGovernorTest,ModelCallConcurrencyGovernorTest,HybridRetrieverTest,TrustedRetrievalServiceTest,PgVectorStoreCacheInvalidationTest,PgIndexGenerationRoutingTest,IndexGenerationPolicyTest,IndexGenerationServiceTest,AgentExecutionBudgetTest,ControlledAgentExecutorTest,CorrelationTraceFilterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test >"${RAGENT_LOG}" 2>&1

timeout_seconds="$(remaining_seconds)"
timeout --signal=TERM --kill-after=15s "${timeout_seconds}s" \
  mvn -o -f "${ROOT_DIR}/pom.xml" -pl paicoding-web -am \
  -Dtest=ArticleKnowledgeGenerationRebuildServiceTest,ArticleKnowledgeGenerationRebuildTaskServiceTest,RagentArticleKnowledgeIndexerTest,RagentKnowledgeSyncServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test >"${PAICODING_LOG}" 2>&1

timeout_seconds="$(remaining_seconds)"
timeout --signal=TERM --kill-after=15s "${timeout_seconds}s" \
  mvn -o -f "${ROOT_DIR}/pom.xml" -pl paicoding-aigc-service -am \
  -Dtest=ArticleKnowledgeMetricsTest,ArticleKnowledgeEventMetricsIntegrationTest,ArticleKnowledgeConsumerTraceIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test >"${AIGC_LOG}" 2>&1

timeout_seconds="$(remaining_seconds)"
timeout --signal=TERM --kill-after=10s "${timeout_seconds}s" \
  bash "${ROOT_DIR}/scripts/check-ragent-integration-static.sh" >"${STATIC_LOG}" 2>&1

rg -q 'BUILD SUCCESS' "${RAGENT_LOG}"
rg -q 'BUILD SUCCESS' "${PAICODING_LOG}"
rg -q 'BUILD SUCCESS' "${AIGC_LOG}"
rg -q 'Ragent integration static checks passed; no service or container was started.' "${STATIC_LOG}"

printf 'rag_fault_regression=SUCCESS\n'
printf 'services_started=NO\n'
printf 'evidence_level=L1_L2\n'
printf 'runtime_dir=%s\n' "${RUNTIME_DIR}"
