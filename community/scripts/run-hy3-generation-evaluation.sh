#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
ARTICLES="${ROOT_DIR}/paicoding-web/src/test/resources/rag/legacy-baseline-articles.tsv"
QUERIES="${ROOT_DIR}/paicoding-web/src/test/resources/rag/legacy-baseline-queries.tsv"
REPORT="${ROOT_DIR}/docs/perf/HY3冻结题集生成质量评测_20260717.md"

[[ -r "${ARTICLES}" ]] || { echo "missing articles fixture: ${ARTICLES}" >&2; exit 1; }
[[ -r "${QUERIES}" ]] || { echo "missing queries fixture: ${QUERIES}" >&2; exit 1; }
[[ -d "${RAGENT_PROJECT_DIR}" ]] || { echo "missing Ragent project: ${RAGENT_PROJECT_DIR}" >&2; exit 1; }
[[ -n "${OPENROUTER_API_KEY:-}" ]] || {
  echo "missing OPENROUTER_API_KEY; export it before running the real evaluation" >&2
  exit 1
}

cd "${RAGENT_PROJECT_DIR}"
MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx512m -XX:+UseSerialGC}" \
  timeout --signal=TERM --kill-after=20s "${RAG_EVAL_TIMEOUT_SECONDS:-300}s" \
  mvn -pl infra-ai -am \
    -Dspotless.apply.skip=true \
    -DskipITs \
    -Dtest=Hy3FrozenGenerationEvaluatorTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Drag.eval.articles="${ARTICLES}" \
    -Drag.eval.queries="${QUERIES}" \
    -Drag.eval.report="${REPORT}" \
    test

echo "HY3 frozen generation evaluation completed: ${REPORT}"
