#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime/article-knowledge-full-chain"
SUMMARY_FILE="${RUNTIME_DIR}/summary.env"
PROGRESS_FILE="${RUNTIME_DIR}/progress.log"
RESULT_FILE="${RUNTIME_DIR}/result.log"
REPORT_FILE="${ROOT_DIR}/docs/perf/文章知识全链路自动验证报告.md"

final_status="$(sed -n 's/^FINAL_STATUS=//p' "${SUMMARY_FILE}" 2>/dev/null | tail -1)"
exit_code="$(sed -n 's/^EXIT_CODE=//p' "${SUMMARY_FILE}" 2>/dev/null | tail -1)"
duration="$(sed -n 's/^DURATION_SECONDS=//p' "${SUMMARY_FILE}" 2>/dev/null | tail -1)"
scope="$(sed -n 's/^VALIDATION_SCOPE=//p' "${SUMMARY_FILE}" 2>/dev/null | tail -1)"
test_summary="$(rg 'Tests run: [0-9]+, Failures:' "${RESULT_FILE}" 2>/dev/null | tail -1 | sed -E 's/\x1B\[[0-9;]*[mK]//g' || true)"

{
  printf '# 文章知识全链路自动验证报告\n\n'
  printf '> 本文件由有边界后台任务结束后自动生成；失败报告同样保留，不能把失败或缺失字段包装成成功。\n\n'
  printf '## 任务结果\n\n'
  printf -- '- 最终状态：`%s`\n' "${final_status:-UNKNOWN}"
  printf -- '- 退出码：`%s`\n' "${exit_code:-UNKNOWN}"
  printf -- '- 耗时：`%s` 秒\n' "${duration:-UNKNOWN}"
  printf -- '- 范围：`%s`\n' "${scope:-UNKNOWN}"
  printf -- '- 测试摘要：`%s`\n\n' "${test_summary:-NOT_AVAILABLE}"
  printf '## 自动证据门禁\n\n'
  for marker in duplicate_event stale_event failure_recovery vector_metadata stable_chunk_id token_metadata hybrid_retrieval metadata_filter trusted_retrieval citation_traceability validation; do
    value="$(rg -o "${marker}=[A-Z_]+" "${PROGRESS_FILE}" 2>/dev/null | tail -1 || true)"
    printf -- '- `%s`\n' "${value:-${marker}=NOT_VERIFIED}"
  done
  printf '\n## 清理边界\n\n'
  printf '任务仅清理固定测试文章、对应 Outbox/索引状态、隔离 Redis 映射、本任务启动的 Ragent/AIGC 进程和本 Compose 项目。\n\n'
  printf '本报告不包含密码、Token、完整文章正文、Prompt 或 MQ payload。\n'
} >"${REPORT_FILE}"

chmod 0644 "${REPORT_FILE}"
echo "report=${REPORT_FILE}"
