#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
MODE="${1:-baseline}"   # baseline | dynamic
THREADS="${THREADS:-8}"
CONNECTIONS="${CONNECTIONS:-800}"
DURATION="${DURATION:-60s}"
TARGET_RPS="${TARGET_RPS:-}"
FAVOR_TOKEN_COUNT="${FAVOR_TOKEN_COUNT:-}"
LOGIN_USERNAME_PREFIX="${LOGIN_USERNAME_PREFIX:-}"
LOGIN_USERNAME_START="${LOGIN_USERNAME_START:-1}"
AUTO_REGISTER_USERS="${AUTO_REGISTER_USERS:-true}"

if [[ -n "${TOKEN:-}" && -z "${FAVOR_TOKEN:-}" ]]; then
  FAVOR_TOKEN="${TOKEN}"
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

fetch_token_by_username_password() {
  local username="$1"
  local password="$2"

  local login_resp status body parsed
  if ! login_resp=$(curl -sS -X POST "${BASE_URL}/new/login/username" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${password}\"}" \
    -w '__HTTP_STATUS__:%{http_code}'); then
    echo "ERR|调用登录接口失败: ${username}"
    return 0
  fi

  status="${login_resp##*__HTTP_STATUS__:}"
  body="${login_resp%__HTTP_STATUS__*}"

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
' <<< "${body}")

  if [[ "${parsed}" == OK\|* ]]; then
    echo "${parsed}"
    return 0
  fi

  if [[ "${AUTO_REGISTER_USERS}" == "true" ]]; then
    local register_resp register_status register_body register_parsed
    if register_resp=$(curl -sS -X POST "${BASE_URL}/login/register" \
      -d "username=${username}&password=${password}" \
      -w '__HTTP_STATUS__:%{http_code}'); then
      register_status="${register_resp##*__HTTP_STATUS__:}"
      register_body="${register_resp%__HTTP_STATUS__*}"
      register_parsed=$(python3 -c 'import json,sys
raw=sys.stdin.read()
try:
    obj=json.loads(raw)
except Exception:
    print("ERR|返回非JSON")
    sys.exit(0)
status=obj.get("status",{})
if status.get("code")!=0:
    print("ERR|注册失败: {}".format(status.get("msg","unknown")))
    sys.exit(0)
print("OK|registered")
' <<< "${register_body}")

      if [[ "${register_status}" == "200" && "${register_parsed}" == OK\|* ]]; then
        if login_resp=$(curl -sS -X POST "${BASE_URL}/new/login/username" \
          -H 'Content-Type: application/json' \
          -d "{\"username\":\"${username}\",\"password\":\"${password}\"}" \
          -w '__HTTP_STATUS__:%{http_code}'); then
          status="${login_resp##*__HTTP_STATUS__:}"
          body="${login_resp%__HTTP_STATUS__*}"
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
' <<< "${body}")
          if [[ "${parsed}" == OK\|* ]]; then
            echo "${parsed}"
            return 0
          fi
        fi
      fi
    fi
  fi

  local body_preview
  body_preview=$(echo "${body}" | tr '\n' ' ' | cut -c1-160)
  echo "ERR|HTTP ${status}, ${parsed#ERR|}, 响应片段: ${body_preview}"
  return 0
}

auto_fetch_tokens_by_count() {
  if [[ -z "${FAVOR_TOKEN_COUNT}" ]]; then
    return 1
  fi

  if ! [[ "${FAVOR_TOKEN_COUNT}" =~ ^[0-9]+$ ]] || [[ "${FAVOR_TOKEN_COUNT}" -le 0 ]]; then
    echo "[ERROR] FAVOR_TOKEN_COUNT 必须是正整数"
    exit 1
  fi

  if ! [[ "${LOGIN_USERNAME_START}" =~ ^[0-9]+$ ]]; then
    echo "[ERROR] LOGIN_USERNAME_START 必须是整数"
    exit 1
  fi

  if [[ -z "${LOGIN_USERNAME_PREFIX}" || -z "${LOGIN_PASSWORD:-}" ]]; then
    echo "[ERROR] 使用 FAVOR_TOKEN_COUNT 模式需要设置 LOGIN_USERNAME_PREFIX 和 LOGIN_PASSWORD"
    echo "[ERROR] 示例:"
    echo "  export FAVOR_TOKEN_COUNT=20"
    echo "  export LOGIN_USERNAME_PREFIX='pressure_user_'"
    echo "  export LOGIN_PASSWORD='123456'"
    echo "  export LOGIN_USERNAME_START=1"
    exit 1
  fi

  if ! curl -sS --max-time 3 "${BASE_URL}/actuator/health" >/dev/null; then
    echo "[ERROR] 无法连接后端: ${BASE_URL}，请先启动服务（例如 8081 端口）"
    return 1
  fi

  echo "[INFO] 按数量自动获取 token: count=${FAVOR_TOKEN_COUNT}, prefix=${LOGIN_USERNAME_PREFIX}, start=${LOGIN_USERNAME_START}"
  echo "[INFO] AUTO_REGISTER_USERS=${AUTO_REGISTER_USERS}"

  local tokens=()
  local idx username parsed token
  for ((idx=0; idx< FAVOR_TOKEN_COUNT; idx++)); do
    username="${LOGIN_USERNAME_PREFIX}$((LOGIN_USERNAME_START + idx))"
    parsed=$(fetch_token_by_username_password "${username}" "${LOGIN_PASSWORD}")
    if [[ "${parsed}" == OK\|* ]]; then
      token="${parsed#OK|}"
      tokens+=("${token}")
    else
      echo "[ERROR] 用户 ${username} 获取 token 失败: ${parsed#ERR|}"
      return 1
    fi
  done

  FAVOR_TOKENS=$(IFS=,; echo "${tokens[*]}")
  export FAVOR_TOKENS
  echo "[INFO] 自动获取多用户 token 成功，数量=${#tokens[@]}"
  return 0
}

if ! command -v wrk >/dev/null 2>&1; then
  echo "[ERROR] wrk 未安装，请先安装: sudo apt-get install -y wrk"
  exit 1
fi

if [[ -z "${FAVOR_TOKENS:-}" ]]; then
  auto_fetch_tokens_by_count || true
fi

if [[ -z "${FAVOR_TOKEN:-}" && -z "${FAVOR_TOKENS:-}" ]]; then
  auto_fetch_token || {
    echo "[ERROR] 请先设置 FAVOR_TOKEN，或设置登录信息自动获取:"
    echo "  export FAVOR_TOKEN='xxxxx'"
    echo "  或"
    echo "  export FAVOR_TOKEN_COUNT=20"
    echo "  export LOGIN_USERNAME_PREFIX='pressure_user_'"
    echo "  export LOGIN_PASSWORD='123456'"
    echo "  export LOGIN_USERNAME_START=1"
    echo "  或"
    echo "  export LOGIN_USERNAME='你的用户名'"
    echo "  export LOGIN_PASSWORD='你的密码'"
    echo "  bash docs/perf/run-favor-pressure.sh baseline"
    echo "  (支持带或不带 Bearer 前缀，脚本会自动兼容)"
    exit 1
  }
fi

if [[ -n "${FAVOR_TOKENS:-}" ]]; then
  echo "[INFO] 检测到 FAVOR_TOKENS，启用多用户 token 轮转压测"
fi

if [[ -z "${FAVOR_ARTICLE_ID:-}" ]]; then
  echo "[WARN] 未设置 FAVOR_ARTICLE_ID，脚本将随机使用 articleId=1~5"
fi

echo "[INFO] BASE_URL=${BASE_URL}"
echo "[INFO] MODE=${MODE}"
echo "[INFO] THREADS=${THREADS}, CONNECTIONS=${CONNECTIONS}, DURATION=${DURATION}"

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

cd "$(dirname "$0")/../.."

if [[ "${MODE}" == "baseline" ]]; then
  wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" --latency -s docs/perf/favor.lua "${BASE_URL}"
elif [[ "${MODE}" == "dynamic" ]]; then
  BASE_URL="${BASE_URL}" THREADS="${THREADS}" CONNECTIONS="${CONNECTIONS}" DURATION="${DURATION}" REDIS_CLI="${REDIS_CLI:-redis-cli}" bash docs/perf/favor-dynamic-tuning.sh
else
  echo "[ERROR] 不支持的模式: ${MODE}，可选 baseline | dynamic"
  exit 1
fi
