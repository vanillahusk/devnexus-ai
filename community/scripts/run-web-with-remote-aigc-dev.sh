#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${PROJECT_DIR}"

mvn -pl paicoding-web -am -DskipTests clean package

java -jar "${PROJECT_DIR}/paicoding-web/target/paicoding-web-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active=dev \
  --ai.knowledge.service.mode=remote \
  --ai.knowledge.service.baseUrl=http://localhost:8094 \
  --ai.knowledge.service.token="${AIGC_INTERNAL_TOKEN:-paicoding-aigc-dev-token}"
