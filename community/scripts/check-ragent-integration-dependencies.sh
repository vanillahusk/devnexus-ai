#!/usr/bin/env bash
set -uo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-16379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
ROCKETMQ_HOST="${ROCKETMQ_HOST:-127.0.0.1}"
ROCKETMQ_NAMESRV_PORT="${ROCKETMQ_NAMESRV_PORT:-9876}"
RAGENT_S3_HOST="${RAGENT_S3_HOST:-127.0.0.1}"
RAGENT_S3_PORT="${RAGENT_S3_PORT:-9000}"
RAGENT_BASE_URL="${RAGENT_BASE_URL:-http://127.0.0.1:9090/api/ragent}"
RAGENT_HOST="${RAGENT_HOST:-127.0.0.1}"
RAGENT_PORT="${RAGENT_PORT:-9090}"
OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://127.0.0.1:11434}"
RAGENT_ALLOW_DETERMINISTIC_EMBEDDING="${RAGENT_ALLOW_DETERMINISTIC_EMBEDDING:-false}"
MIN_AVAILABLE_MEMORY_MB="${MIN_AVAILABLE_MEMORY_MB:-2500}"
RAGENT_REQUIRE_SERVICE="${RAGENT_REQUIRE_SERVICE:-true}"
RAGENT_REQUIRE_MYSQL="${RAGENT_REQUIRE_MYSQL:-true}"

failures=()

ok() {
  echo "[OK] $1"
}

fail() {
  echo "[FAIL] $1"
  failures+=("$1")
}

check_tcp() {
  local name="$1" host="$2" port="$3"
  if nc -z -w 1 "$host" "$port" >/dev/null 2>&1; then
    ok "$name TCP $host:$port"
    return 0
  fi
  fail "$name 不可连接：$host:$port"
  return 1
}

echo "Ragent 增量索引真实验证依赖预检"

if [[ "${RAGENT_REQUIRE_MYSQL}" != "true" ]]; then
  ok "当前仅验证 Ragent 适配器，MySQL 留给完整事件链路验证"
elif command -v mysql >/dev/null 2>&1 \
    && MYSQL_PWD="$MYSQL_PASSWORD" mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" \
      -N -e "SELECT 1" >/dev/null 2>&1; then
  ok "MySQL 鉴权与查询 $MYSQL_HOST:$MYSQL_PORT"
else
  fail "MySQL 不可用或凭据错误：$MYSQL_HOST:$MYSQL_PORT"
fi

redis_args=(-h "$REDIS_HOST" -p "$REDIS_PORT")
if [[ -n "$REDIS_PASSWORD" ]]; then
  redis_args+=(-a "$REDIS_PASSWORD")
fi
if command -v redis-cli >/dev/null 2>&1 \
    && [[ "$(redis-cli "${redis_args[@]}" PING 2>/dev/null | tail -n 1)" == "PONG" ]]; then
  ok "Redis PING $REDIS_HOST:$REDIS_PORT"
else
  fail "Redis 不可用或凭据错误：$REDIS_HOST:$REDIS_PORT"
fi

check_tcp "PostgreSQL/pgvector" "$POSTGRES_HOST" "$POSTGRES_PORT" || true
check_tcp "RocketMQ NameServer" "$ROCKETMQ_HOST" "$ROCKETMQ_NAMESRV_PORT" || true
check_tcp "Ragent S3 object storage" "$RAGENT_S3_HOST" "$RAGENT_S3_PORT" || true

if [[ "${RAGENT_REQUIRE_SERVICE}" != "true" ]]; then
  ok "Ragent 服务由有界运行器托管，将在基础设施预检后启动"
elif check_tcp "Ragent" "$RAGENT_HOST" "$RAGENT_PORT"; then
  login_response="$(curl -sS --max-time 3 -H 'Content-Type: application/json' \
    -d "{\"username\":\"${RAGENT_USERNAME:-admin}\",\"password\":\"${RAGENT_PASSWORD:-admin}\"}" \
    "$RAGENT_BASE_URL/auth/login" 2>/dev/null || true)"
  ragent_token="$(printf '%s' "$login_response" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  if printf '%s' "$login_response" | rg -q '"code"[[:space:]]*:[[:space:]]*"0"' \
      && [[ -n "$ragent_token" ]]; then
    protected_response="$(curl -sS --max-time 3 -H "Authorization: ${ragent_token}" \
      "$RAGENT_BASE_URL/user/me" 2>/dev/null || true)"
    if printf '%s' "$protected_response" | rg -q '"code"[[:space:]]*:[[:space:]]*"0"'; then
      ok "Ragent 登录、原始 Sa-Token Authorization 与受保护 API 就绪"
    else
      fail "Ragent 登录成功，但受保护 API 鉴权失败"
    fi
  else
    fail "Ragent 端口已监听，但登录未返回可用 Token"
  fi
fi

if [[ -n "${SILICONFLOW_API_KEY:-}" ]]; then
  ok "已配置 SiliconFlow Embedding Provider 密钥（未输出密钥）"
else
  ollama_tags="$(curl -fsS --max-time 2 "$OLLAMA_BASE_URL/api/tags" 2>/dev/null || true)"
  if printf '%s' "$ollama_tags" | rg -q 'qwen3-embedding'; then
    ok "Ollama qwen3-embedding 模型可用"
  elif [[ "${RAGENT_ALLOW_DETERMINISTIC_EMBEDDING}" == "true" ]]; then
    ok "已显式允许确定性验证向量（仅验证索引基础设施，不代表语义检索质量）"
  else
    fail "没有 SiliconFlow Embedding 密钥，Ollama qwen3-embedding 不可用，且未显式允许验证向量"
  fi
fi

available_memory_mb="$(awk '/MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo)"
if [[ -n "$available_memory_mb" && "$available_memory_mb" -ge "$MIN_AVAILABLE_MEMORY_MB" ]]; then
  ok "可用内存 ${available_memory_mb}MB（最低 ${MIN_AVAILABLE_MEMORY_MB}MB）"
else
  fail "可用内存不足：${available_memory_mb:-unknown}MB，最低要求 ${MIN_AVAILABLE_MEMORY_MB}MB"
fi

if (( ${#failures[@]} > 0 )); then
  echo
  echo "预检失败：不会启动 Ragent、AIGC 或其他下游 Java 服务。"
  printf ' - %s\n' "${failures[@]}"
  exit 1
fi

echo
echo "预检通过：可以进入有界的 Ragent 增量索引验证。"
