#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
REPORT="${ROOT_DIR}/docs/perf/HY3受控Agent真实规划评测_20260718.md"

[[ -d "${RAGENT_PROJECT_DIR}" ]] || { echo "missing Ragent project: ${RAGENT_PROJECT_DIR}" >&2; exit 1; }
[[ -n "${OPENROUTER_API_KEY:-}" ]] || {
  echo "missing OPENROUTER_API_KEY; export it before running the real evaluation" >&2
  exit 1
}

if [[ "${MODEL_API_DIRECT:-false}" == "true" ]]; then
  unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy
fi

cd "${RAGENT_PROJECT_DIR}"
MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx512m -XX:+UseSerialGC}" \
  timeout --signal=TERM --kill-after=20s "${AGENT_EVALUATION_TIMEOUT_SECONDS:-300}s" \
  mvn -pl bootstrap -am \
    -Dspotless.apply.skip=true \
    -DskipITs \
    -Dtest=Hy3AgentPlannerEvaluatorTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dagent.eval.report="${REPORT}" \
    test

echo "HY3 Agent Planner evaluation completed: ${REPORT}"
