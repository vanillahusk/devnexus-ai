#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AGENT_DIR="${SKYWALKING_AGENT_DIR:-${ROOT_DIR}/.runtime/skywalking-agent}"
AGENT_IMAGE="${SKYWALKING_AGENT_IMAGE:-apache/skywalking-java-agent:9.6.0-java17}"

mkdir -p "${AGENT_DIR}"

docker run --rm \
  --entrypoint sh \
  -v "${AGENT_DIR}:/target" \
  "${AGENT_IMAGE}" \
  -c 'cp -a /skywalking/agent/. /target/ &&
      cp /skywalking/agent/optional-plugins/apm-spring-cloud-gateway-4.x-plugin-9.6.0.jar /target/plugins/ &&
      cp /skywalking/agent/optional-plugins/apm-spring-webflux-6.x-plugin-9.6.0.jar /target/plugins/ &&
      mkdir -p /target/logs && chmod 0777 /target/logs'

test -f "${AGENT_DIR}/skywalking-agent.jar"

echo "SkyWalking Java Agent is ready: ${AGENT_DIR}/skywalking-agent.jar"
