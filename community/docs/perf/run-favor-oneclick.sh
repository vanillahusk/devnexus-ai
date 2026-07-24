#!/usr/bin/env bash
set -euo pipefail

USER_COUNT="${USER_COUNT:-${1:-20}}"
MODE="${MODE:-all}" # all | baseline | dynamic
BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
THREADS="${THREADS:-8}"
CONNECTIONS="${CONNECTIONS:-800}"
DURATION="${DURATION:-60s}"
LOGIN_USERNAME_PREFIX="${LOGIN_USERNAME_PREFIX:-pressure_user_}"
LOGIN_USERNAME_START="${LOGIN_USERNAME_START:-1}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-}"
TARGET_REAL_RPS="${TARGET_REAL_RPS:-1000}"
RUN_TS="$(date +%Y%m%d-%H%M%S)"

if ! [[ "${USER_COUNT}" =~ ^[0-9]+$ ]] || [[ "${USER_COUNT}" -le 0 ]]; then
  echo "[ERROR] USER_COUNT 必须是正整数，当前: ${USER_COUNT}"
  exit 1
fi

if [[ -z "${LOGIN_PASSWORD}" ]]; then
  echo "[ERROR] 请先设置 LOGIN_PASSWORD（用于自动登录批量用户获取 token）"
  echo "[ERROR] 示例: LOGIN_PASSWORD='123456' USER_COUNT=20 bash docs/perf/run-favor-oneclick.sh"
  exit 1
fi

if [[ "${MODE}" != "all" && "${MODE}" != "baseline" && "${MODE}" != "dynamic" ]]; then
  echo "[ERROR] MODE 仅支持 all | baseline | dynamic，当前: ${MODE}"
  exit 1
fi

duration_seconds() {
  local d="$1"
  if [[ "${d}" =~ ^([0-9]+)s$ ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  if [[ "${d}" =~ ^([0-9]+)m$ ]]; then
    echo "$((BASH_REMATCH[1] * 60))"
    return
  fi
  if [[ "${d}" =~ ^([0-9]+)h$ ]]; then
    echo "$((BASH_REMATCH[1] * 3600))"
    return
  fi
  if [[ "${d}" =~ ^[0-9]+$ ]]; then
    echo "${d}"
    return
  fi
  echo "60"
}

calc_recommendation() {
  local duration_sec="$1"
  local article_factor="$2"
  local target_rps="$3"

  local per_user_quota=$(( (duration_sec * 5 * article_factor) / 60 ))
  if [[ "${per_user_quota}" -lt 1 ]]; then
    per_user_quota=1
  fi

  local required_users=$(( (target_rps * duration_sec + per_user_quota - 1) / per_user_quota ))
  local suggest_low=$(( required_users ))
  local suggest_high=$(( required_users * 12 / 10 ))
  if [[ "${suggest_high}" -lt "${suggest_low}" ]]; then
    suggest_high="${suggest_low}"
  fi

  echo "${per_user_quota}|${suggest_low}|${suggest_high}"
}

WORK_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${WORK_DIR}"

export BASE_URL THREADS CONNECTIONS DURATION
export FAVOR_TOKEN_COUNT="${USER_COUNT}"
export LOGIN_USERNAME_PREFIX LOGIN_USERNAME_START LOGIN_PASSWORD

if [[ -n "${FAVOR_ARTICLE_ID:-}" ]]; then
  export FAVOR_ARTICLE_ID
fi

echo "[INFO] 一键点赞压测参数"
echo "[INFO] MODE=${MODE}, USER_COUNT=${USER_COUNT}, BASE_URL=${BASE_URL}"
echo "[INFO] THREADS=${THREADS}, CONNECTIONS=${CONNECTIONS}, DURATION=${DURATION}"
echo "[INFO] LOGIN_USERNAME_PREFIX=${LOGIN_USERNAME_PREFIX}, LOGIN_USERNAME_START=${LOGIN_USERNAME_START}"

duration_sec="$(duration_seconds "${DURATION}")"
if [[ -n "${FAVOR_ARTICLE_ID:-}" ]]; then
  article_factor=1
  echo "[INFO] FAVOR_ARTICLE_ID=${FAVOR_ARTICLE_ID}（固定文章，限流最严格）"
else
  article_factor=5
  echo "[INFO] 未固定 FAVOR_ARTICLE_ID（默认 1~5 随机分摊限流）"
fi

IFS='|' read -r per_user_quota suggest_low suggest_high <<< "$(calc_recommendation "${duration_sec}" "${article_factor}" "${TARGET_REAL_RPS}")"

printf '[INFO] 限流上限估算: 当前 USER_COUNT=%s 时，%ss 内每用户最多约 %s 次真实点赞请求\n' "${USER_COUNT}" "${duration_sec}" "${per_user_quota}"
printf '[INFO] 限流上限估算: 目标真实吞吐 TARGET_REAL_RPS=%s 时，建议 USER_COUNT≈%s~%s\n' "${TARGET_REAL_RPS}" "${suggest_low}" "${suggest_high}"

if [[ "${USER_COUNT}" -lt "${suggest_low}" ]]; then
  echo "[WARN] 当前 USER_COUNT 可能偏小，容易被限流提前打满，建议先提高到 ${suggest_low}+ 再压测。"
fi

run_case() {
  local case_name="$1"
  local log_file="/tmp/favor-${case_name}-${RUN_TS}.log"
  echo "[INFO] 开始 ${case_name} 压测，日志: ${log_file}"
  bash docs/perf/run-favor-pressure.sh "${case_name}" | tee "${log_file}"
}

extract_metric() {
  local log_file="$1"
  local key="$2"
  grep -E "${key}" "${log_file}" | tail -1
}

baseline_log=""
dynamic_log=""

if [[ "${MODE}" == "all" || "${MODE}" == "baseline" ]]; then
  baseline_log="/tmp/favor-baseline-${RUN_TS}.log"
  echo "[INFO] 开始 baseline 压测，日志: ${baseline_log}"
  bash docs/perf/run-favor-pressure.sh baseline | tee "${baseline_log}"
fi

if [[ "${MODE}" == "all" || "${MODE}" == "dynamic" ]]; then
  dynamic_log="/tmp/favor-dynamic-${RUN_TS}.log"
  echo "[INFO] 开始 dynamic 压测，日志: ${dynamic_log}"
  bash docs/perf/run-favor-pressure.sh dynamic | tee "${dynamic_log}"
fi

echo ""
echo "========== SUMMARY =========="
if [[ -n "${baseline_log}" ]]; then
  echo "[baseline] $(extract_metric "${baseline_log}" 'Requests/sec:')"
  echo "[baseline] $(extract_metric "${baseline_log}" '^\s*99%')"
fi
if [[ -n "${dynamic_log}" ]]; then
  echo "[dynamic ] $(extract_metric "${dynamic_log}" 'Requests/sec:')"
  echo "[dynamic ] $(extract_metric "${dynamic_log}" '^\s*99%')"
fi
echo "============================="

if [[ -n "${baseline_log}" && -n "${dynamic_log}" ]]; then
  echo "[INFO] 基线日志: ${baseline_log}"
  echo "[INFO] 动态日志: ${dynamic_log}"
fi
