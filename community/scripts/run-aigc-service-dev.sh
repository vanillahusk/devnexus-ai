#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${PROJECT_DIR}/scripts/java-observability-args.sh"
configure_java_observability "aigc-service" "${PROJECT_DIR}"
configure_spring_profiles

cd "${PROJECT_DIR}"

mvn -pl paicoding-aigc-service -am -DskipTests clean package

java "${JAVA_OBSERVABILITY_ARGS[@]}" -jar "${PROJECT_DIR}/paicoding-aigc-service/target/paicoding-aigc-service-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active="${SPRING_ACTIVE_PROFILES}" \
  --server.port=8094 \
  --spring.application.name=aigc-service \
  --ai.knowledge.service.token=paicoding-aigc-dev-token \
  --spring.cloud.nacos.discovery.enabled="${NACOS_DISCOVERY_ENABLED:-false}" \
  --spring.cloud.nacos.discovery.server-addr="${NACOS_ADDR:-127.0.0.1:8848}"
