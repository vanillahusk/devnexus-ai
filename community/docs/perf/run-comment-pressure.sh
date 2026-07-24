#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
THREADS="${THREADS:-8}"
CONNECTIONS="${CONNECTIONS:-200}"
DURATION="${DURATION:-60s}"
TARGET_RPS="${TARGET_RPS:-}"

if [[ -n "${TOKEN:-}" && -z "${FAVOR_TOKEN:-}" ]]; then
  FAVOR_TOKEN="${TOKEN}"
fi

if [[ -n "${ARTICLE_ID:-}" && -z "${COMMENT_ARTICLE_ID:-}" ]]; then
  COMMENT_ARTICLE_ID="${ARTICLE_ID}"
fi

auto_fetch_token() {
  if [[ -z "${LOGIN_USERNAME:-}" || -z "${LOGIN_PASSWORD:-}" ]]; then
    return 1
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    echo "[ERROR] 需要 python3 解析登录返回结果，请先安装 python3 或手动设置 FAVOR_TOKEN"
    exit 1
  fi

  echo "[INFO] 未检测到 FAVOR_TOKEN，尝试使用 LOGIN_USERNAME/LOGIN_PASSWORD 自动登录获取 token..."

  if ! curl -sS --max-time 3 "${BASE_URL}/actuator/health" >/dev/null; then
    echo "[ERROR] 无法连接后端: ${BASE_URL}，请先启动服务（例如 8081 端口）"
    return 1
  fi

  local login_resp
  if ! login_resp=$(curl -sS -X POST "${BASE_URL}/new/login/username" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${LOGIN_USERNAME}\",\"password\":\"${LOGIN_PASSWORD}\"}"); then
    echo "[ERROR] 调用登录接口失败，请确认后端可访问且账号密码正确"
    return 1
  fi

  local parsed
  parsed=$(python3 -c 'import json,sys
raw=sys.stdin.read()
try:
    obj=json.loads(raw)
except Exception:
    print("ERR|返回非JSON")
    sys.exit(0)
status=obj.get("status",{})
if status.get("code")!=0:
    print("ERR|登录失败: {}".format(status.get("msg","unknown")))
    sys.exit(0)
token=(obj.get("result") or {}).get("token")
if not token:
    print("ERR|登录成功但未返回 token")
    sys.exit(0)
print("OK|"+token)
' <<< "${login_resp}")

  if [[ "${parsed}" == OK\|* ]]; then
    FAVOR_TOKEN="${parsed#OK|}"
    echo "[INFO] 自动获取 token 成功"
    return 0
  fi

  echo "[ERROR] ${parsed#ERR|}"
  return 1
}

if ! command -v wrk >/dev/null 2>&1; then
  echo "[ERROR] wrk 未安装，请先安装: sudo apt-get install -y wrk"
  exit 1
fi

if [[ -z "${FAVOR_TOKEN:-}" ]]; then
  auto_fetch_token || {
    echo "[ERROR] 请先设置 FAVOR_TOKEN，或设置登录信息自动获取:"
    echo "  export FAVOR_TOKEN='xxxxx'"
    echo "  或: TOKEN='xxxxx' ./docs/perf/run-comment-pressure.sh"
    echo "  或"
    echo "  export LOGIN_USERNAME='你的用户名'"
    echo "  export LOGIN_PASSWORD='你的密码'"
    echo "  ./docs/perf/run-comment-pressure.sh"
    echo "  (支持带或不带 Bearer 前缀，脚本会自动兼容)"
    exit 1
  }
fi

if [[ -z "${COMMENT_ARTICLE_ID:-}" ]]; then
  echo "[WARN] 未设置 COMMENT_ARTICLE_ID，默认使用 14"
fi

echo "[INFO] BASE_URL=${BASE_URL}"
echo "[INFO] THREADS=${THREADS}, CONNECTIONS=${CONNECTIONS}, DURATION=${DURATION}"
echo "[INFO] COMMENT_ARTICLE_ID=${COMMENT_ARTICLE_ID:-14}"
echo "[INFO] COMMENT_PARENT_ID=${COMMENT_PARENT_ID:-0}"
echo "[INFO] COMMENT_TOP_ID=${COMMENT_TOP_ID:-0}"

if [[ -n "${TARGET_RPS}" ]]; then
  if ! [[ "${TARGET_RPS}" =~ ^[0-9]+([.][0-9]+)?$ ]] || [[ "${TARGET_RPS}" == "0" ]]; then
    echo "[ERROR] TARGET_RPS 必须是正数"
    exit 1
  fi
  REQUEST_DELAY_MS=$(awk -v c="${CONNECTIONS}" -v r="${TARGET_RPS}" \
    'BEGIN { value = (1000 * c / r); if (value < 1) value = 1; printf "%.3f", value }')
  export REQUEST_DELAY_MS
  echo "[INFO] TARGET_RPS=${TARGET_RPS}, REQUEST_DELAY_MS=${REQUEST_DELAY_MS}（实际值以 wrk 输出为准）"
fi

# wrk 的 Lua 脚本通过环境变量读取 token；shell 内赋值不会自动传给子进程。
export FAVOR_TOKEN
export COMMENT_ARTICLE_ID="${COMMENT_ARTICLE_ID:-14}"
export COMMENT_PARENT_ID="${COMMENT_PARENT_ID:-0}"
export COMMENT_TOP_ID="${COMMENT_TOP_ID:-0}"

cd "$(dirname "$0")/../.."

wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" --latency -s docs/perf/comment-write.lua "${BASE_URL}"
