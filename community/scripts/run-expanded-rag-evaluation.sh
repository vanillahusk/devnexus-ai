#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent}"
MODE="${MODE:-offline}"
ARTICLES="${ROOT_DIR}/paicoding-web/src/test/resources/rag/expanded-hard-articles.tsv"
QUERIES="${ROOT_DIR}/paicoding-web/src/test/resources/rag/expanded-hard-queries.tsv"
OFFLINE_REPORT="${ROOT_DIR}/docs/perf/RAG扩大规模困难集_BM25预检_20260720.md"
REAL_REPORT="${ROOT_DIR}/docs/perf/RAG扩大规模困难集_Qwen3评测_20260720.md"

python3 "${ROOT_DIR}/scripts/generate-expanded-rag-evaluation-fixtures.py"

[[ -r "${ARTICLES}" ]] || { echo "missing articles fixture: ${ARTICLES}" >&2; exit 1; }
[[ -r "${QUERIES}" ]] || { echo "missing queries fixture: ${QUERIES}" >&2; exit 1; }
[[ -d "${RAGENT_PROJECT_DIR}" ]] || { echo "missing Ragent project: ${RAGENT_PROJECT_DIR}" >&2; exit 1; }

case "${MODE}" in
  offline)
    TEST_CLASS="ModernBm25OfflineEvaluatorTest"
    REPORT="${OFFLINE_REPORT}"
    TIMEOUT_SECONDS="${RAG_EVAL_TIMEOUT_SECONDS:-180}"
    ;;
  real)
    [[ -n "${SILICONFLOW_API_KEY:-}" ]] || {
      echo "missing SILICONFLOW_API_KEY; export it before MODE=real" >&2
      exit 1
    }
    TEST_CLASS="Qwen3DenseHybridOfflineEvaluatorTest"
    REPORT="${REAL_REPORT}"
    TIMEOUT_SECONDS="${RAG_EVAL_TIMEOUT_SECONDS:-480}"
    ;;
  *)
    echo "unsupported MODE=${MODE}; expected offline or real" >&2
    exit 2
    ;;
esac

cd "${RAGENT_PROJECT_DIR}"
MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx512m -XX:+UseSerialGC}" \
  timeout --signal=TERM --kill-after=20s "${TIMEOUT_SECONDS}s" \
  env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY \
  mvn -pl bootstrap -am \
    -Dspotless.apply.skip=true \
    -DskipITs \
    -Dtest="${TEST_CLASS}" \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Drag.eval.profile=expanded \
    -Drag.eval.articles="${ARTICLES}" \
    -Drag.eval.queries="${QUERIES}" \
    -Drag.eval.report="${REPORT}" \
    test

echo "expanded RAG evaluation completed mode=${MODE}: ${REPORT}"
