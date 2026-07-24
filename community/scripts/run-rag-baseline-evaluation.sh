#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_PATH="${RAG_BASELINE_REPORT:-${ROOT_DIR}/paicoding-web/target/rag/legacy-baseline-report.md}"

cd "${ROOT_DIR}"
mvn -pl paicoding-web -am \
  -Dtest=LegacyRetrievalPolicyTest,AiKnowledgeAssistantRecallBaselineTest,LegacyRagBaselineEvaluatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine=-Djdk.attach.allowAttachSelf=true \
  -Drag.baseline.report="${REPORT_PATH}" \
  test

printf 'RAG baseline report: %s\n' "${REPORT_PATH}"
