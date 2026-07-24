#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/ragent-integration"
status="$(cat "${RUNTIME_DIR}/status" 2>/dev/null || echo NOT_SUBMITTED)"
worker_pid="$(cat "${RUNTIME_DIR}/worker.pid" 2>/dev/null || true)"

echo "status=${status}"
case "${status}" in
  SUCCESS_CLEANED_UP|FAILED_CLEANED_UP|TIMED_OUT_CLEANED_UP|CANCELLED_CLEANED_UP|NOT_SUBMITTED)
    ;;
  *)
    if ! [[ "${worker_pid}" =~ ^[0-9]+$ ]] \
        || ! kill -0 "${worker_pid}" 2>/dev/null; then
      echo "effective_status=ORPHANED_NO_WORKER"
    fi
    ;;
esac
if [[ -f "${RUNTIME_DIR}/worker.pid" ]]; then
  echo "worker_pid=${worker_pid}"
fi
if [[ -f "${RUNTIME_DIR}/child.pid" ]]; then
  echo "child_pid=$(cat "${RUNTIME_DIR}/child.pid")"
fi
if [[ -f "${RUNTIME_DIR}/ragent.pid" ]]; then
  echo "ragent_pid=$(cat "${RUNTIME_DIR}/ragent.pid")"
fi
if [[ -f "${RUNTIME_DIR}/summary.env" ]]; then
  echo '[summary]'
  cat "${RUNTIME_DIR}/summary.env"
fi
if [[ -f "${RUNTIME_DIR}/progress.log" ]]; then
  echo '[progress]'
  tail -n 40 "${RUNTIME_DIR}/progress.log"
fi
if [[ -f "${RUNTIME_DIR}/result.log" ]]; then
  echo '[result]'
  tail -n 80 "${RUNTIME_DIR}/result.log"
fi
if [[ -f "${RUNTIME_DIR}/ragent-build.log" ]]; then
  echo '[ragent-build]'
  tail -n 60 "${RUNTIME_DIR}/ragent-build.log"
fi
if [[ -f "${RUNTIME_DIR}/ragent.log" ]]; then
  echo '[ragent]'
  tail -n 80 "${RUNTIME_DIR}/ragent.log"
fi
