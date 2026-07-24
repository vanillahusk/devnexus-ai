#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/article-knowledge-full-chain"
WORKER_SCRIPT="${ROOT_DIR}/scripts/run-article-knowledge-full-chain-worker.sh"
PID_FILE="${RUNTIME_DIR}/worker.pid"

mkdir -p "${RUNTIME_DIR}"
existing_pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
if [[ "${existing_pid}" =~ ^[0-9]+$ ]] \
    && kill -0 "${existing_pid}" 2>/dev/null \
    && ps -p "${existing_pid}" -o args= | rg -Fq "${WORKER_SCRIPT}"; then
  echo "Article knowledge full-chain validation is already running, pid=${existing_pid}" >&2
  exit 1
fi

rm -f "${RUNTIME_DIR}/status" "${RUNTIME_DIR}/progress.log" \
  "${RUNTIME_DIR}/result.log" "${RUNTIME_DIR}/summary.env" \
  "${RUNTIME_DIR}/ragent.log" "${RUNTIME_DIR}/aigc.log" "${PID_FILE}"
printf 'SUBMITTING\n' >"${RUNTIME_DIR}/status"

nohup bash "${WORKER_SCRIPT}" </dev/null >/dev/null 2>&1 &
worker_pid=$!

echo "Article knowledge full-chain validation submitted, pid=${worker_pid}"
echo "The submit command exits now; the worker has a bounded timeout and cleanup trap."
echo "Inspect: bash scripts/article-knowledge-full-chain-status.sh"
echo "Stop: bash scripts/stop-article-knowledge-full-chain.sh"
