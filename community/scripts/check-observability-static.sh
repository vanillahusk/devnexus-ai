#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

OBSERVABILITY_SCRIPTS=(
  scripts/start-skywalking-evidence-containers.sh
  scripts/run-observability-evidence-worker.sh
  scripts/collect-skywalking-evidence.sh
  scripts/generate-observability-report.sh
  scripts/submit-observability-evidence.sh
  scripts/observability-evidence-status.sh
  scripts/stop-observability-evidence.sh
)

echo '[1/6] Shell syntax'
bash -n "${OBSERVABILITY_SCRIPTS[@]}"

echo '[2/6] Docker Compose rendering (configuration only; no containers are started)'
# docker-compose 1.x otherwise probes the Docker socket even for `config`.
# Pinning the API version keeps this check daemon-independent and read-only.
DOCKER_API_VERSION="${DOCKER_API_VERSION:-1.41}" \
  docker-compose --profile observability -f ops/docker-compose.yml config >/dev/null

echo '[3/6] Grafana dashboard JSON'
node -e '
const fs = require("fs");
const path = require("path");
const dir = "ops/grafana/dashboards";
for (const name of fs.readdirSync(dir).filter(n => n.endsWith(".json"))) {
  const value = JSON.parse(fs.readFileSync(path.join(dir, name), "utf8"));
  if (!value.title || !Array.isArray(value.panels)) throw new Error(`invalid dashboard: ${name}`);
}
'

echo '[4/6] Prometheus and Grafana YAML structure'
python3 - <<'PY'
from pathlib import Path
import yaml

prometheus = yaml.safe_load(Path("ops/prometheus/prometheus.yml").read_text())
assert prometheus["global"]["scrape_interval"] == "15s"
assert prometheus["global"]["evaluation_interval"] == "15s"
jobs = {item["job_name"] for item in prometheus["scrape_configs"]}
expected = {
    "paicoding-web",
    "paicoding-gateway",
    "paicoding-auth-service",
    "paicoding-message-service",
    "paicoding-aigc-service",
    "ragent-service",
}
assert expected <= jobs, f"missing Prometheus jobs: {expected - jobs}"
job_by_name = {item["job_name"]: item for item in prometheus["scrape_configs"]}
assert job_by_name["ragent-service"]["metrics_path"] == "/api/ragent/actuator/prometheus"
for path in Path("ops/prometheus/rules").glob("*.yml"):
    value = yaml.safe_load(path.read_text())
    assert value.get("groups"), f"Prometheus rule groups missing: {path}"
for path in Path("ops/grafana/provisioning").rglob("*.yml"):
    yaml.safe_load(path.read_text())
PY

echo '[5/6] Logback XML and task state machine / cleanup invariants'
python3 - <<'PY'
from xml.etree import ElementTree
ElementTree.parse("ops/observability/logback-observability.xml")
PY

worker="scripts/run-observability-evidence-worker.sh"
for state in STARTING PREFLIGHT WAITING_SERVICES COLLECTING SUCCESS SUCCESS_CLEANED_UP FAILED_CLEANED_UP SUCCESS_CLEANUP_FAILED FAILED_CLEANUP_FAILED; do
  grep -q "${state}" "${worker}"
done
grep -q "trap cleanup EXIT" "${worker}"
grep -q "cleanup=finished" "${worker}"
grep -q 'code.*==.*200' "${worker}"
grep -q 'consecutive_required' "${worker}"
for dependency in 'mysql.*3306' 'redis.*6379' 'rocketmq-nameserver.*9876' 'skywalking-oap-http.*12800' 'skywalking-oap-grpc.*11800'; do
  grep -Eq "${dependency}" "${worker}"
done

starter="scripts/start-skywalking-evidence-containers.sh"
auth_line="$(grep -n 'start_and_wait paicoding-evidence-auth' "${worker}" | cut -d: -f1)"
message_line="$(grep -n 'start_and_wait paicoding-evidence-message' "${worker}" | cut -d: -f1)"
web_line="$(grep -n 'start_and_wait paicoding-evidence-web' "${worker}" | cut -d: -f1)"
gateway_line="$(grep -n 'start_and_wait paicoding-evidence-gateway' "${worker}" | cut -d: -f1)"
((auth_line < message_line && message_line < web_line && web_line < gateway_line))
grep -q 'SW_AGENT_SAMPLE_N_PER_3_SECS' "${starter}"
grep -q 'logging.config' "${starter}"
grep -q 'EVIDENCE_LOW_RESOURCE_MODE' "${starter}"
grep -q 'paicoding-skywalking-banyandb' "${starter}"
grep -q 'stale_container_ids' "${starter}"
grep -q 'docker rm -f' "${starter}"
grep -Eq 'actuator/prometheus 384m' "${starter}"
grep -Eq 'actuator/prometheus 512m' "${starter}"

echo '[6/6] Resource, evidence and report-generation invariants'
grep -q 'storage.tsdb.retention.time=2h' ops/docker-compose.yml
grep -q 'storage.tsdb.retention.size=256MB' ops/docker-compose.yml
grep -q '/prometheus:size=' ops/docker-compose.yml
grep -q '/prometheus:size=.*uid=65534' ops/docker-compose.yml
grep -q '/data:size=' ops/docker-compose.yml
grep -q '/var/lib/grafana:size=' ops/docker-compose.yml
grep -q '/var/lib/grafana:size=.*uid=472' ops/docker-compose.yml
collector="scripts/collect-skywalking-evidence.sh"
for evidence in 'normal gateway' 'slow gateway' 'error gateway' 'real comment event' 'prometheus online targets' 'SkyWalking services' 'SkyWalking traces' 'SkyWalking topology' 'producer/consumer asynchronous segment'; do
  grep -qi "${evidence}" "${collector}"
done
grep -q 'queryTraces(condition' "${collector}"
grep -q 'retrievedTimeRange' "${collector}"
grep -q 'graphql_document' "${collector}"
if grep -Eq 'queryBasicTraces|needTotal|traces\{[^}]*\}[[:space:]]+total' "${collector}"; then
  echo 'legacy SkyWalking trace pagination fields detected' >&2
  exit 1
fi
grep -q 'generate-observability-report.sh' "${worker}"
grep -q 'login.private.json' scripts/generate-observability-report.sh
grep -q 'chmod 0644' scripts/generate-observability-report.sh
grep -q 'register deterministic evidence user' "${collector}"
grep -q 'chmod 0600.*login.private.json' "${collector}"
if sed -n '/<<EOF/,/^EOF$/p' scripts/generate-observability-report.sh | grep -q '^```'; then
  echo 'unsafe Markdown fence found in an unquoted heredoc' >&2
  exit 1
fi

rag_rules="ops/prometheus/rules/rag-agent-rules.yml"
rag_dashboard="ops/grafana/dashboards/rag-agent-dashboard.json"
for signal in rag_index_outbox_events rag_index_sync_latency_seconds_bucket \
    rag_embedding_batches_total rag_model_calls_duration_seconds_count \
    rag_retrieval_degraded_total rag_retrieval_requests_total rag_agent_terminations_total; do
  grep -q "${signal}" "${rag_rules}"
done
for signal in rag_index_outbox_events rag_index_sync_latency_seconds_bucket \
    rag_embedding_chunks_total rag_retrieval_stage_duration_seconds_bucket \
    rag_retrieval_requests_total rag_model_calls_inflight rag_agent_requests_total \
    rag_agent_terminations_total rag_agent_tokens_sum; do
  grep -q "${signal}" "${rag_dashboard}"
done
if grep -Eq 'userId|articleId|eventId|queryHash|conversationId' "${rag_rules}" "${rag_dashboard}"; then
  echo 'high-cardinality RAG label detected in Prometheus/Grafana configuration' >&2
  exit 1
fi

echo 'observability_static_check=SUCCESS'
echo 'services_started=NO'
