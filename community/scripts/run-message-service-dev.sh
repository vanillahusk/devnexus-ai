#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/java-observability-args.sh"
configure_java_observability "message-service" "${ROOT_DIR}"
configure_spring_profiles

cd "${ROOT_DIR}"

mvn -pl paicoding-message-service -am -DskipTests clean package

java "${JAVA_OBSERVABILITY_ARGS[@]}" -jar "${ROOT_DIR}/paicoding-message-service/target/paicoding-message-service-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active="${SPRING_ACTIVE_PROFILES}" \
  --server.port=8095 \
  --spring.application.name=message-service \
  --message.service.token=paicoding-message-dev-token \
  --spring.cloud.nacos.discovery.enabled="${NACOS_DISCOVERY_ENABLED:-false}" \
  --spring.cloud.nacos.discovery.server-addr="${NACOS_ADDR:-127.0.0.1:8848}"
