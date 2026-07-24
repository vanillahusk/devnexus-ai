#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/article-knowledge-full-chain"
echo "status=$(cat "${RUNTIME_DIR}/status" 2>/dev/null || echo NOT_STARTED)"
for file in summary.env progress.log result.log ragent.log aigc.log; do
  if [[ -f "${RUNTIME_DIR}/${file}" ]]; then
    echo "[${file}]"
    tail -n 80 "${RUNTIME_DIR}/${file}"
  fi
done
