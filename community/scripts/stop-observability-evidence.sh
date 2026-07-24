#!/usr/bin/env bash
set -euo pipefail

docker stop \
  paicoding-observability-evidence-job \
  paicoding-evidence-auth \
  paicoding-evidence-message \
  paicoding-evidence-web \
  paicoding-evidence-gateway \
  paicoding-prometheus \
  paicoding-grafana \
  paicoding-skywalking-ui \
  paicoding-skywalking-oap \
  paicoding-skywalking-banyandb >/dev/null 2>&1 || true

echo 'observability evidence services stopped'
