#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIGC_JAR="${AIGC_JAR:-${ROOT_DIR}/paicoding-aigc-service/target/paicoding-aigc-service-0.0.1-SNAPSHOT.jar}"

if [[ ! -s "${AIGC_JAR}" ]]; then
  echo "AIGC executable jar is missing: ${AIGC_JAR}" >&2
  exit 1
fi

exec java \
  -Xms128m -Xmx384m -XX:+UseSerialGC \
  -jar "${AIGC_JAR}" \
  --server.port="${AIGC_PORT:-8094}" \
  --server.shutdown=graceful \
  --spring.lifecycle.timeout-per-shutdown-phase=15s \
  --spring.datasource.url="jdbc:mysql://${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3306}/pai_coding?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
  --spring.datasource.username="${MYSQL_USER:-root}" \
  --spring.datasource.password="${MYSQL_PASSWORD:-123456}" \
  --spring.data.redis.host="${REDIS_HOST:-127.0.0.1}" \
  --spring.data.redis.port="${REDIS_PORT:-16379}" \
  --spring.data.redis.password="${REDIS_PASSWORD:-123456}" \
  --paicoding.mq.provider=rocketmq \
  --paicoding.mq.rocketmq.name-server="${ROCKETMQ_HOST:-127.0.0.1}:${ROCKETMQ_NAMESRV_PORT:-9876}" \
  --rocketmq.name-server="${ROCKETMQ_HOST:-127.0.0.1}:${ROCKETMQ_NAMESRV_PORT:-9876}" \
  --ai.knowledge.ragent.enabled=true \
  --ai.knowledge.ragent.auto-sync=false \
  --ai.knowledge.ragent.base-url="${RAGENT_BASE_URL:-http://127.0.0.1:9090/api/ragent}" \
  --ai.knowledge.ragent.username="${RAGENT_USERNAME:-admin}" \
  --ai.knowledge.ragent.password="${RAGENT_PASSWORD:-admin}" \
  --ai.knowledge.ragent.embedding-model="${RAGENT_EMBEDDING_MODEL:-validation-embedding-1536}" \
  --ai.knowledge.ragent.collection-name="${RAGENT_COLLECTION_NAME:-paicodingcommunitykb}" \
  --ai.knowledge.ragent.chunk-wait-timeout-ms=120000 \
  --ai.knowledge.ragent.chunk-poll-interval-ms=1000 \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false \
  --spring.liquibase.enabled=false \
  --paicoding.mq.outbox.flush-delay-ms=3600000 \
  --management.endpoints.web.exposure.include=health,info \
  --logging.level.root=WARN \
  --logging.level.com.github.paicoding.forum=WARN \
  --logging.level.com.github.paicoding.forum.aigc.mq.ArticleKnowledgeRocketMqConsumer=INFO \
  --logging.level.org.springframework=WARN \
  --mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl \
  --logging.file.name=
