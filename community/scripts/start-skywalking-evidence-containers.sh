#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${SKYWALKING_AGENT_IMAGE:-apache/skywalking-java-agent:9.6.0-java17}"
AGENT_DIR="${SKYWALKING_AGENT_DIR:-${ROOT_DIR}/.runtime/skywalking-agent}"
OAP_ADDR="${SKYWALKING_OAP_ADDR:-127.0.0.1:11800}"
RUN_MODE="${EVIDENCE_RUN_MODE:-start}"

test -f "${AGENT_DIR}/skywalking-agent.jar"

# docker-compose 1.29 cannot always recreate containers produced from newer
# image metadata (KeyError: ContainerConfig). These observability containers
# hold only disposable evidence data, so recreate them from a clean boundary.
for name in \
  paicoding-prometheus \
  paicoding-grafana \
  paicoding-skywalking-banyandb \
  paicoding-skywalking-oap \
  paicoding-skywalking-ui; do
  mapfile -t stale_container_ids < <(docker ps -aq --filter "name=${name}")
  if ((${#stale_container_ids[@]} > 0)); then
    docker rm -f "${stale_container_ids[@]}" >/dev/null 2>&1 || true
  fi
done

observability_services=(prometheus skywalking-banyandb skywalking-oap)
if [[ "${EVIDENCE_LOW_RESOURCE_MODE:-true}" != "true" ]]; then
  observability_services+=(grafana skywalking-ui)
fi
docker-compose --profile observability -f "${ROOT_DIR}/ops/docker-compose.yml" up -d \
  "${observability_services[@]}"

for name in paicoding-evidence-auth paicoding-evidence-message paicoding-evidence-web paicoding-evidence-gateway; do
  docker rm -f "${name}" >/dev/null 2>&1 || true
done

start_service() {
  local container_name="$1"
  local service_name="$2"
  local jar_path="$3"
  local health_url="$4"
  local max_heap="$5"
  shift 5

  local docker_action=(run -d)
  if [[ "${RUN_MODE}" == "create" ]]; then
    docker_action=(create)
  fi

  docker "${docker_action[@]}" \
    --name "${container_name}" \
    --network host \
    -e "JAVA_TOOL_OPTIONS=-Xms128m -Xmx${max_heap} -javaagent:/skywalking/agent/skywalking-agent.jar" \
    -e "SW_AGENT_NAME=${service_name}" \
    -e "SW_AGENT_INSTANCE_NAME=${service_name}-evidence" \
    -e "SW_AGENT_COLLECTOR_BACKEND_SERVICES=${OAP_ADDR}" \
    -e "SW_AGENT_SAMPLE_N_PER_3_SECS=${SKYWALKING_SAMPLE_N_PER_3_SECS:-1}" \
    -e SW_LOGGING_OUTPUT=CONSOLE \
    -v "${ROOT_DIR}:/workspace:ro" \
    -v "${AGENT_DIR}:/skywalking/agent" \
    -w /workspace \
    "${IMAGE}" \
    java -jar "${jar_path}" \
    --logging.config=file:/workspace/ops/observability/logback-observability.xml \
    "$@" >/dev/null

  if [[ "${RUN_MODE}" == "create" ]]; then
    echo "${service_name}=created"
    return 0
  fi

  local code
  for ((i=1; i<=180; i++)); do
    code="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' "${health_url}" 2>/dev/null || true)"
    if [[ "${code}" == "200" ]]; then
      echo "${service_name}=ready(http:${code}, seconds:${i})"
      return 0
    fi
    if ! docker inspect "${container_name}" --format '{{.State.Running}}' | grep -q true; then
      docker logs --tail 80 "${container_name}" >&2
      return 1
    fi
    sleep 1
  done
  echo "${service_name}=timeout" >&2
  docker logs --tail 80 "${container_name}" >&2
  return 1
}

start_service paicoding-evidence-auth auth-service \
  paicoding-auth-service/target/paicoding-auth-service-0.0.1-SNAPSHOT.jar \
  http://127.0.0.1:8093/actuator/prometheus 384m \
  --spring.profiles.active=dev \
  --server.port=8093 \
  --auth.service.token=paicoding-auth-dev-token \
  --observability.probe.enabled=true \
  --spring.cloud.nacos.discovery.enabled=false

start_service paicoding-evidence-message message-service \
  paicoding-message-service/target/paicoding-message-service-0.0.1-SNAPSHOT.jar \
  http://127.0.0.1:8095/actuator/prometheus 384m \
  --spring.profiles.active=dev \
  --server.port=8095 \
  --spring.cloud.nacos.discovery.enabled=false

start_service paicoding-evidence-web forum-service \
  paicoding-web/target/paicoding-web-0.0.1-SNAPSHOT.jar \
  http://127.0.0.1:8081/actuator/prometheus 512m \
  --spring.profiles.active=dev \
  --server.port=8081 \
  --spring.liquibase.enabled=false \
  --spring.cloud.nacos.discovery.enabled=false \
  --service.discovery.prefer-registry=false \
  --auth.service.mode=remote \
  --auth.service.token=paicoding-auth-dev-token \
  --message.service.mode=remote \
  --message.service.token=paicoding-message-dev-token

start_service paicoding-evidence-gateway paicoding-gateway \
  paicoding-gateway/target/paicoding-gateway-0.0.1-SNAPSHOT.jar \
  http://127.0.0.1:10010/actuator/prometheus 384m \
  --spring.profiles.active=dev \
  --spring.cloud.nacos.discovery.enabled=false \
  --gateway.auth.internal-token=paicoding-auth-dev-token

docker ps --filter 'name=paicoding-evidence-' \
  --format '{{.Names}}\t{{.Status}}'
