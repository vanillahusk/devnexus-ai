#!/usr/bin/env bash

configure_java_observability() {
  local service_name="$1"
  local root_dir="$2"
  local agent_dir="${SKYWALKING_AGENT_DIR:-${root_dir}/.runtime/skywalking-agent}"

  JAVA_OBSERVABILITY_ARGS=()
  if [[ "${SKYWALKING_ENABLED:-false}" == "true" ]]; then
    if [[ ! -f "${agent_dir}/skywalking-agent.jar" ]]; then
      echo "SkyWalking agent is missing. Run scripts/prepare-skywalking-agent.sh first." >&2
      return 1
    fi
    JAVA_OBSERVABILITY_ARGS=(
      "-javaagent:${agent_dir}/skywalking-agent.jar"
      "-Dskywalking.agent.service_name=${service_name}"
      "-Dskywalking.agent.instance_name=${service_name}-${HOSTNAME:-local}"
      "-Dskywalking.collector.backend_service=${SKYWALKING_OAP_ADDR:-127.0.0.1:11800}"
      "-Dskywalking.logging.output=CONSOLE"
    )
  fi
}

configure_spring_profiles() {
  SPRING_ACTIVE_PROFILES=dev
  if [[ "${NACOS_DISCOVERY_ENABLED:-false}" == "true" ]]; then
    SPRING_ACTIVE_PROFILES=dev,nacos-config
  fi
}
