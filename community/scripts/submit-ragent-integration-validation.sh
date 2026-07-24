#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/ragent-integration"
WORKER_SCRIPT="${ROOT_DIR}/scripts/run-ragent-integration-validation-worker.sh"
PID_FILE="${RUNTIME_DIR}/worker.pid"

mkdir -p "${RUNTIME_DIR}"

if [[ -f "${PID_FILE}" ]]; then
  existing_pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
  if [[ "${existing_pid}" =~ ^[0-9]+$ ]] \
      && kill -0 "${existing_pid}" 2>/dev/null \
      && ps -p "${existing_pid}" -o args= | rg -Fq "${WORKER_SCRIPT}"; then
    echo "Ragent validation is already running, pid=${existing_pid}"
    exit 1
  fi
fi

rm -f "${RUNTIME_DIR}/status" "${RUNTIME_DIR}/progress.log" \
  "${RUNTIME_DIR}/result.log" "${RUNTIME_DIR}/summary.env" \
  "${RUNTIME_DIR}/ragent-build.log" "${RUNTIME_DIR}/ragent.log" \
  "${RUNTIME_DIR}/child.pid" "${RUNTIME_DIR}/ragent.pid" "${PID_FILE}"
printf 'SUBMITTING\n' >"${RUNTIME_DIR}/status"

nohup bash "${WORKER_SCRIPT}" </dev/null >/dev/null 2>&1 &
worker_pid=$!

echo "Ragent integration validation submitted, pid=${worker_pid}"
echo "status: ${RUNTIME_DIR}/status"
echo "progress: ${RUNTIME_DIR}/progress.log"
echo "result: ${RUNTIME_DIR}/result.log"
echo "inspect: bash scripts/ragent-integration-validation-status.sh"
