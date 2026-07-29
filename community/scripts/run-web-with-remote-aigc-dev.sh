#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_DIR}"

mvn -pl paicoding-web -am -DskipTests clean package

java -jar "${PROJECT_DIR}/paicoding-web/target/paicoding-web-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active=dev \
  --ai.knowledge.service.mode=remote \
  --ai.knowledge.service.baseUrl=http://localhost:8094 \
  --ai.knowledge.service.token=paicoding-aigc-dev-token
