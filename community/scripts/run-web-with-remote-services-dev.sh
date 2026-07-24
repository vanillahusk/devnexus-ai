#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/java-observability-args.sh"
configure_java_observability "forum-service" "${ROOT_DIR}"
configure_spring_profiles

cd "${ROOT_DIR}"

mvn -pl paicoding-web -am -DskipTests clean package

java "${JAVA_OBSERVABILITY_ARGS[@]}" -jar "${ROOT_DIR}/paicoding-web/target/paicoding-web-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active="${SPRING_ACTIVE_PROFILES}" \
  --spring.liquibase.enabled=false \
  --auth.service.mode=remote \
  --auth.service.serviceId=auth-service \
  --auth.service.token=paicoding-auth-dev-token \
  --ai.knowledge.service.mode=remote \
  --ai.knowledge.service.serviceId=aigc-service \
  --ai.knowledge.service.token=paicoding-aigc-dev-token \
  --message.service.mode=remote \
  --message.service.serviceId=message-service \
  --message.service.token=paicoding-message-dev-token \
  --spring.cloud.nacos.discovery.enabled="${NACOS_DISCOVERY_ENABLED:-false}" \
  --spring.cloud.nacos.discovery.server-addr="${NACOS_ADDR:-127.0.0.1:8848}" \
  --service.discovery.prefer-registry="${NACOS_DISCOVERY_ENABLED:-false}"
