#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/ragent-integration"
WORKER_SCRIPT="${ROOT_DIR}/scripts/run-ragent-integration-validation-worker.sh"
PID_FILE="${RUNTIME_DIR}/worker.pid"

if [[ ! -f "${PID_FILE}" ]]; then
  echo "Ragent integration validation is not running"
  exit 0
fi

worker_pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
if ! [[ "${worker_pid}" =~ ^[0-9]+$ ]] \
    || ! kill -0 "${worker_pid}" 2>/dev/null \
    || ! ps -p "${worker_pid}" -o args= | rg -Fq "${WORKER_SCRIPT}"; then
  echo "Stale worker PID file found; no process was stopped"
  rm -f "${PID_FILE}"
  exit 0
fi

kill -TERM "${worker_pid}"
for _ in {1..15}; do
  if ! kill -0 "${worker_pid}" 2>/dev/null; then
    echo "Ragent integration validation stopped and cleaned up"
    exit 0
  fi
  sleep 1
done

echo "Worker did not exit within 15 seconds; inspect with scripts/ragent-integration-validation-status.sh"
exit 1

