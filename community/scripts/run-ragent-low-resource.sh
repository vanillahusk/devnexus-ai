#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
RAGENT_JAR="${RAGENT_JAR:-${RAGENT_PROJECT_DIR}/bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar}"

if [[ ! -s "${RAGENT_JAR}" ]]; then
  echo "Ragent executable jar is missing: ${RAGENT_JAR}" >&2
  echo "Build it with scripts/build-ragent-integration-artifact.sh" >&2
  exit 1
fi

mkdir -p "${RAGENT_JAVA_TMPDIR:-/tmp/ragent-paicoding-validation}"

redis_password_args=()
if [[ -n "${REDIS_PASSWORD:-}" ]]; then
  redis_password_args+=("--spring.data.redis.password=${REDIS_PASSWORD}")
fi

exec java \
  -Xms128m -Xmx384m -XX:+UseSerialGC \
  -Djava.io.tmpdir="${RAGENT_JAVA_TMPDIR:-/tmp/ragent-paicoding-validation}" \
  -jar "${RAGENT_JAR}" \
  --server.port="${RAGENT_PORT:-9090}" \
  --server.shutdown=graceful \
  --spring.lifecycle.timeout-per-shutdown-phase=15s \
  --spring.datasource.url="jdbc:postgresql://${POSTGRES_HOST:-127.0.0.1}:${POSTGRES_PORT:-5432}/ragent?client_encoding=UTF8&connectTimeout=5&socketTimeout=10" \
  --spring.datasource.username="${POSTGRES_USER:-postgres}" \
  --spring.datasource.password="${POSTGRES_PASSWORD:-postgres}" \
  --spring.datasource.hikari.maximum-pool-size=3 \
  --spring.datasource.hikari.minimum-idle=1 \
  --spring.data.redis.host="${REDIS_HOST:-127.0.0.1}" \
  --spring.data.redis.port="${REDIS_PORT:-16379}" \
  "${redis_password_args[@]}" \
  --rocketmq.name-server="${ROCKETMQ_HOST:-127.0.0.1}:${ROCKETMQ_NAMESRV_PORT:-9876}" \
  --rustfs.url="http://${RAGENT_S3_HOST:-127.0.0.1}:${RAGENT_S3_PORT:-9000}" \
  --rag.vector.type=pg \
  --ai.embedding.default-model="${RAGENT_EMBEDDING_MODEL:-qwen-emb-8b}" \
  --ai.validation.embedding.enabled="${RAGENT_VALIDATION_EMBEDDING_ENABLED:-false}" \
  --rag.query-rewrite.enabled=false \
  --rag.trace.enabled=false \
  --rag.rate-limit.global.max-concurrent=1 \
  --server.tomcat.threads.max=24 \
  --server.tomcat.threads.min-spare=2 \
  --logging.level.root=WARN \
  --logging.level.com.nageoffer.ai.ragent=WARN \
  --logging.level.org.springframework=WARN \
  --logging.level.org.mybatis=WARN \
  --logging.file.name=
