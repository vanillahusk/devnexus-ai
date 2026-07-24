#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/observability"
ARTIFACT_DIR="${RUNTIME_DIR}/artifacts"
PROGRESS_FILE="${RUNTIME_DIR}/progress.log"
RESULT_FILE="${RUNTIME_DIR}/result.log"
STATUS_FILE="${RUNTIME_DIR}/status"
REPORT_FILE="${OBSERVABILITY_REPORT_FILE:-${ROOT_DIR}/docs/perf/可观测性与SkyWalking验证报告.md}"
FINAL_STATUS="${EVIDENCE_FINAL_STATUS:-$(cat "${STATUS_FILE}" 2>/dev/null || printf 'UNKNOWN')}"
EXIT_CODE="${EVIDENCE_EXIT_CODE:-UNKNOWN}"
GENERATED_AT="$(date -Iseconds)"
TEMP_REPORT="$(mktemp "${RUNTIME_DIR}/report.XXXXXX")"

cleanup_temp() {
  rm -f "${TEMP_REPORT}"
}
trap cleanup_temp EXIT

append_code_file() {
  local title="$1"
  local file="$2"
  local max_lines="${3:-120}"
  printf '\n### %s\n\n' "${title}" >>"${TEMP_REPORT}"
  if [[ -s "${file}" ]]; then
    printf '```text\n' >>"${TEMP_REPORT}"
    sed -n "1,${max_lines}p" "${file}" \
      | sed -E \
          -e 's/("token"[[:space:]]*:[[:space:]]*")[^"]+/\1[REDACTED]/g' \
          -e 's/(Authorization:[[:space:]]*)[^[:space:]]+/\1[REDACTED]/g' \
          -e 's/(x-access-token:[[:space:]]*)[^[:space:]]+/\1[REDACTED]/g' \
      | awk 'length($0) > 12000 { print substr($0, 1, 12000) " ... [本行已截断，完整内容见原始证据文件]"; next } { print }' \
      >>"${TEMP_REPORT}"
    printf '\n```\n' >>"${TEMP_REPORT}"
  else
    printf '未采集到该项；任务可能在到达此阶段前已经失败。\n' >>"${TEMP_REPORT}"
  fi
}

artifact_state() {
  local file="$1"
  if [[ -s "${ARTIFACT_DIR}/${file}" ]]; then
    printf '已采集'
  else
    printf '未采集'
  fi
}

cat >"${TEMP_REPORT}" <<EOF
# 可观测性与 SkyWalking 验证报告

> 本文档由 \`scripts/generate-observability-report.sh\` 自动生成。它记录一次实际验证任务的结果，不把“代码已实现”冒充为“运行验证已通过”。

## 一、任务结论

| 项目 | 结果 |
| --- | --- |
| 生成时间 | ${GENERATED_AT} |
| 最终状态 | ${FINAL_STATUS} |
| Worker 退出码 | ${EXIT_CODE} |
| 清理状态 | $(grep 'cleanup=finished' "${PROGRESS_FILE}" 2>/dev/null | tail -n 1 | sed 's/^.*cleanup=finished //' || true) |

判定规则：只有最终状态为 \`SUCCESS_CLEANED_UP\`，并且下列证据均为“已采集”，才能表述为本轮完整验证通过。失败报告仍会保留已完成阶段和明确失败位置。

## 二、证据完整性

| 验证项 | 证据状态 | 原始证据 |
| --- | --- | --- |
| 正常 Gateway → Auth | $(artifact_state normal-auth.json) | \`.runtime/observability/artifacts/normal-auth.json\` |
| 正常 Gateway → Message | $(artifact_state normal-message.json) | \`.runtime/observability/artifacts/normal-message.json\` |
| 受控慢请求 | $(artifact_state slow.json) | \`.runtime/observability/artifacts/slow.json\` |
| 受控异常请求 | $(artifact_state error.json) | \`.runtime/observability/artifacts/error.json\` |
| 真实评论 RocketMQ 事件 | $(artifact_state comment.json) | \`.runtime/observability/artifacts/comment.json\` |
| Prometheus 在线目标 | $(artifact_state prometheus-targets.json) | \`.runtime/observability/artifacts/prometheus-targets.json\` |
| JVM、线程池、连接池指标 | $(artifact_state prometheus-runtime-metrics.json) | \`.runtime/observability/artifacts/prometheus-runtime-metrics.json\` |
| OAP 服务列表 | $(artifact_state oap-services.json) | \`.runtime/observability/artifacts/oap-services.json\` |
| SkyWalking Trace | $(artifact_state oap-traces.json) | \`.runtime/observability/artifacts/oap-traces.json\` |
| SkyWalking Trace Span 明细 | $(artifact_state oap-trace-details.jsonl) | \`.runtime/observability/artifacts/oap-trace-details.jsonl\` |
| SkyWalking 服务拓扑 | $(artifact_state oap-topology.json) | \`.runtime/observability/artifacts/oap-topology.json\` |
| RocketMQ 生产/消费异步段 | $(artifact_state oap-async-search.txt) | \`.runtime/observability/artifacts/oap-async-search.txt\` |

## 三、服务启动耗时与依赖预检

Worker 必须先取得 OAP \`/healthcheck\` 连续 HTTP 200，并经过稳定等待；随后检查 MySQL、Redis、RocketMQ NameServer、OAP HTTP 和 OAP gRPC 端口。任一依赖不可用时，不启动四个 Java 服务。
EOF

append_code_file '启动、预检、状态机与清理记录' "${PROGRESS_FILE}" 180

cat >>"${TEMP_REPORT}" <<'EOF'

## 四、Prometheus 指标证据

在线目标应包含 Web、Gateway、Auth、Message 四个任务；运行指标至少验证 JVM 线程和 Hikari/JDBC 连接池，动态线程池存在时同时收集 executor 指标。
EOF
append_code_file 'Prometheus 在线目标' "${ARTIFACT_DIR}/prometheus-targets.json" 80
append_code_file 'JVM、线程池与连接池指标' "${ARTIFACT_DIR}/prometheus-runtime-metrics.json" 100

cat >>"${TEMP_REPORT}" <<'EOF'

## 五、跨服务、慢接口与异常链路

业务请求显式携带 `X-Trace-Id`，报告只记录 Trace ID，不记录登录 Token。慢请求由 Auth 的验证专用端点稳定延迟 1500ms；异常请求由同一端点稳定返回 5xx。
EOF
append_code_file '跨服务业务 Trace ID' "${ARTIFACT_DIR}/business-trace-ids.txt" 30
append_code_file '采集任务结果摘要' "${RESULT_FILE}" 120
append_code_file 'SkyWalking Trace 查询结果' "${ARTIFACT_DIR}/oap-traces.json" 100
append_code_file 'SkyWalking Trace Span 明细' "${ARTIFACT_DIR}/oap-trace-details.jsonl" 120

cat >>"${TEMP_REPORT}" <<'EOF'

## 六、服务列表、拓扑与 RocketMQ 异步链路

完整通过时，OAP 服务列表和拓扑应包含 `paicoding-gateway`、`forum-service`、`auth-service`、`message-service`。真实评论请求应在 Trace 或拓扑中留下 RocketMQ producer/consumer 异步段，证明 HTTP Trace 与消息链路均被观测，而不只是验证 Broker 端口。
EOF
append_code_file 'OAP 服务列表' "${ARTIFACT_DIR}/oap-services.json" 80
append_code_file 'SkyWalking 服务拓扑' "${ARTIFACT_DIR}/oap-topology.json" 100
append_code_file 'RocketMQ 异步段检索证据' "${ARTIFACT_DIR}/oap-async-search.txt" 120

cat >>"${TEMP_REPORT}" <<EOF

## 七、成功、失败与清理结果

- 本轮最终状态：\`${FINAL_STATUS}\`
- Worker 退出码：\`${EXIT_CODE}\`
- 无论成功或失败，Worker 都执行容器停止清理并生成本报告。
- 如果状态不是 \`SUCCESS_CLEANED_UP\`，应从“启动、预检、状态机与清理记录”的最后一个阶段定位原因，修复后重新提交，不能在简历或面试中宣称该项已经通过实测。
- 登录响应保存在私有运行时文件 \`.runtime/observability/artifacts/login.private.json\`，生成器不会将其写入报告。
EOF

cat >>"${TEMP_REPORT}" <<'EOF'
## 八、复现命令

先自行确保 MySQL、Redis 和 RocketMQ 已运行，再提交有运行边界的后台任务：

```bash
bash scripts/submit-observability-evidence.sh
bash scripts/observability-evidence-status.sh
```

仅执行静态检查，不启动任何服务：

```bash
bash scripts/check-observability-static.sh
```
EOF

mkdir -p "$(dirname "${REPORT_FILE}")"
mv "${TEMP_REPORT}" "${REPORT_FILE}"
chmod 0644 "${REPORT_FILE}"
trap - EXIT
printf 'report_generated=%s\n' "${REPORT_FILE}"
