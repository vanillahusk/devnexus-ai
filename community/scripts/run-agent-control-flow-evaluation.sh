#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent}"
TIMEOUT_SECONDS="${AGENT_EVALUATION_TIMEOUT_SECONDS:-180}"

[[ -f "${RAGENT_PROJECT_DIR}/pom.xml" ]] || {
  echo "Ragent project not found: ${RAGENT_PROJECT_DIR}" >&2
  exit 2
}

echo "Running deterministic Agent control-flow task set. This does not evaluate real LLM quality."
cd "${RAGENT_PROJECT_DIR}"
MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx512m -XX:+UseSerialGC}" \
  timeout --signal=TERM --kill-after=20s "${TIMEOUT_SECONDS}" \
  mvn -o -pl bootstrap -am \
  -Dspotless.apply.skip=true \
  -DskipITs \
  -Dtest=ControlledAgentFixedTaskSetTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
