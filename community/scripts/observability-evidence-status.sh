#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/observability"
REPORT_FILE="${ROOT_DIR}/docs/perf/可观测性与SkyWalking验证报告.md"

echo "status=$(cat "${RUNTIME_DIR}/status" 2>/dev/null || echo NOT_SUBMITTED)"
if [[ -f "${RUNTIME_DIR}/progress.log" ]]; then
  echo '[progress]'
  tail -n 30 "${RUNTIME_DIR}/progress.log"
fi
if [[ -f "${REPORT_FILE}" ]]; then
  echo "report=${REPORT_FILE}"
fi
if [[ -f "${RUNTIME_DIR}/result.log" ]]; then
  echo '[result]'
  tail -n 80 "${RUNTIME_DIR}/result.log"
fi
