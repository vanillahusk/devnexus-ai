#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/java-observability-args.sh"
configure_java_observability "auth-service" "${ROOT_DIR}"
configure_spring_profiles

cd "${ROOT_DIR}"

mvn -pl paicoding-auth-service -am -DskipTests clean package

java "${JAVA_OBSERVABILITY_ARGS[@]}" -jar "${ROOT_DIR}/paicoding-auth-service/target/paicoding-auth-service-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active="${SPRING_ACTIVE_PROFILES}" \
  --server.port=8093 \
  --spring.application.name=auth-service \
  --auth.service.token=paicoding-auth-dev-token \
  --spring.cloud.nacos.discovery.enabled="${NACOS_DISCOVERY_ENABLED:-false}" \
  --spring.cloud.nacos.discovery.server-addr="${NACOS_ADDR:-127.0.0.1:8848}"
