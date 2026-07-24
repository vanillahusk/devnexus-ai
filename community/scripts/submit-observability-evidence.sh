#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/observability"
JOB_NAME="paicoding-observability-evidence-job"
IMAGE="${SKYWALKING_AGENT_IMAGE:-apache/skywalking-java-agent:9.6.0-java17}"

mkdir -p "${RUNTIME_DIR}"
rm -f "${RUNTIME_DIR}/status" "${RUNTIME_DIR}/progress.log" "${RUNTIME_DIR}/result.log"
rm -rf "${RUNTIME_DIR}/artifacts"
mkdir -p "${RUNTIME_DIR}/artifacts"
printf 'SUBMITTING\n' >"${RUNTIME_DIR}/status"

docker rm -f "${JOB_NAME}" >/dev/null 2>&1 || true
EVIDENCE_RUN_MODE=create bash "${ROOT_DIR}/scripts/start-skywalking-evidence-containers.sh"

printf 'SUBMITTED\n' >"${RUNTIME_DIR}/status"
docker run -d \
  --name "${JOB_NAME}" \
  --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /usr/bin/docker:/usr/local/bin/docker:ro \
  -v "${ROOT_DIR}:/workspace" \
  -w /workspace \
  --entrypoint /bin/bash \
  "${IMAGE}" \
  scripts/run-observability-evidence-worker.sh >/dev/null

echo "observability evidence job submitted: ${JOB_NAME}"
echo "status: ${RUNTIME_DIR}/status"
echo "progress: ${RUNTIME_DIR}/progress.log"
echo "result: ${RUNTIME_DIR}/result.log"
echo "report: ${ROOT_DIR}/docs/perf/可观测性与SkyWalking验证报告.md"
