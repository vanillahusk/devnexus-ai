#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
ARTICLES="${ROOT_DIR}/paicoding-web/src/test/resources/rag/legacy-baseline-articles.tsv"
QUERIES="${ROOT_DIR}/paicoding-web/src/test/resources/rag/legacy-baseline-queries.tsv"
REPORT="${ROOT_DIR}/docs/perf/Qwen3真实Dense与Hybrid离线评测报告_20260717.md"

[[ -r "${ARTICLES}" ]] || { echo "missing articles fixture: ${ARTICLES}" >&2; exit 1; }
[[ -r "${QUERIES}" ]] || { echo "missing queries fixture: ${QUERIES}" >&2; exit 1; }
[[ -d "${RAGENT_PROJECT_DIR}" ]] || { echo "missing Ragent project: ${RAGENT_PROJECT_DIR}" >&2; exit 1; }
[[ -n "${SILICONFLOW_API_KEY:-}" ]] || {
  echo "missing SILICONFLOW_API_KEY; export it before running the real evaluation" >&2
  exit 1
}

cd "${RAGENT_PROJECT_DIR}"
MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx512m -XX:+UseSerialGC}" \
  timeout --signal=TERM --kill-after=20s "${RAG_EVAL_TIMEOUT_SECONDS:-300}s" \
  mvn -pl bootstrap -am \
    -Dspotless.apply.skip=true \
    -DskipITs \
    -Dtest=Qwen3DenseHybridOfflineEvaluatorTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Drag.eval.articles="${ARTICLES}" \
    -Drag.eval.queries="${QUERIES}" \
    -Drag.eval.report="${REPORT}" \
    test

echo "Qwen3 Dense/Hybrid offline evaluation completed: ${REPORT}"
