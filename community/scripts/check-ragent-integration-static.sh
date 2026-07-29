#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent-main}"
COMPOSE_FILE="${ROOT_DIR}/ops/ragent/docker-compose.yml"
scripts=(
  check-ragent-integration-dependencies.sh
  build-ragent-integration-artifact.sh
  run-ragent-low-resource.sh
  start-ragent-low-resource-dependencies.sh
  stop-ragent-low-resource-dependencies.sh
  ragent-low-resource-dependencies-status.sh
  submit-ragent-integration-validation.sh
  run-ragent-integration-validation-worker.sh
  ragent-integration-validation-status.sh
  stop-ragent-integration-validation.sh
  run-aigc-knowledge-low-resource.sh
  run-article-knowledge-full-chain-worker.sh
  article-knowledge-full-chain-status.sh
  run-modern-rag-bm25-evaluation.sh
  run-agent-control-flow-evaluation.sh
  run-agent-quota-real-validation.sh
  run-retrieval-cache-real-validation.sh
  run-rag-fault-regression.sh
  submit-article-knowledge-full-chain.sh
  stop-article-knowledge-full-chain.sh
  generate-article-knowledge-full-chain-report.sh
)

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

for script in "${scripts[@]}"; do
  path="${ROOT_DIR}/scripts/${script}"
  [[ -x "${path}" ]] || fail "script is not executable: ${script}"
  bash -n "${path}" || fail "shell syntax: ${script}"
done
echo "[OK] shell syntax and executable permissions"

[[ -r "${RAGENT_PROJECT_DIR}/resources/database/schema_pg.sql" ]] \
  || fail "Ragent PostgreSQL schema is missing"
[[ -r "${RAGENT_PROJECT_DIR}/resources/database/init_data_pg.sql" ]] \
  || fail "Ragent PostgreSQL initial data is missing"
rg -q 'CREATE EXTENSION IF NOT EXISTS vector' \
  "${RAGENT_PROJECT_DIR}/resources/database/schema_pg.sql" \
  || fail "pgvector extension initialization is missing"
echo "[OK] associated Ragent PostgreSQL initialization"

RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR}" docker-compose --profile ragent \
  -p paicoding-ragent-low-resource -f "${COMPOSE_FILE}" config >/dev/null \
  || fail "Docker Compose configuration"
echo "[OK] low-resource Docker Compose configuration"

RAGENT_APPLICATION="${RAGENT_PROJECT_DIR}/bootstrap/src/main/resources/application.yaml"
RAGENT_FRONTEND_API="${RAGENT_PROJECT_DIR}/frontend/src/services/api.ts"
RAGENT_POM="${RAGENT_PROJECT_DIR}/pom.xml"
RAGENT_VALIDATION_CLIENT="${RAGENT_PROJECT_DIR}/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/DeterministicValidationEmbeddingClient.java"
RAGENT_HYBRID_RETRIEVER="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/HybridRetriever.java"
RAGENT_METADATA_FILTERS="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgMetadataFilters.java"
RAGENT_DIVERSITY_POLICY="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalDiversityPolicy.java"
RAGENT_EMBEDDING_GOVERNOR="${RAGENT_PROJECT_DIR}/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/EmbeddingCallGovernor.java"
RAGENT_MODEL_CALL_GOVERNOR="${RAGENT_PROJECT_DIR}/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelCallConcurrencyGovernor.java"
RAGENT_ROUTING_LLM="${RAGENT_PROJECT_DIR}/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/RoutingLLMService.java"
RAGENT_ROUTING_RERANK="${RAGENT_PROJECT_DIR}/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/rerank/RoutingRerankService.java"
RAGENT_MEMORY_SERVICE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/DefaultConversationMemoryService.java"
RAGENT_MEMORY_STORE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemoryStore.java"
RAGENT_MEMORY_SUMMARY="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemorySummaryService.java"
RAGENT_THREAD_POOLS="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/ThreadPoolExecutorConfig.java"
RAGENT_HTTP_CONFIG="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/HttpClientConfig.java"
RAGENT_TRUSTED_RETRIEVAL="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/TrustedRetrievalService.java"
RAGENT_EVIDENCE_POLICY="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/EvidenceDecisionPolicy.java"
RAGENT_TRUSTED_PROMPT="${RAGENT_PROJECT_DIR}/bootstrap/src/main/resources/prompt/answer-chat-trusted.st"
RAGENT_CITATION_VALIDATOR="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/CitationValidator.java"
RAGENT_AGENT_TOOLS="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/AgentToolName.java"
RAGENT_AGENT_BUDGET="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/AgentExecutionBudget.java"
RAGENT_AGENT_EXECUTOR="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/ControlledAgentExecutor.java"
RAGENT_AGENT_POOL="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/AgentExecutorConfig.java"
RAGENT_AGENT_CONTROLLER="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/ControlledAgentController.java"
RAGENT_AGENT_QUOTA="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/quota/RedisAgentQuotaService.java"
RAGENT_AGENT_QUOTA_PROPERTIES="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/quota/AgentQuotaProperties.java"
RAGENT_AGENT_QUOTA_REAL_TEST="${RAGENT_PROJECT_DIR}/bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/agent/quota/RedisAgentQuotaRealIntegrationTest.java"
RAGENT_AGENT_USAGE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/usage/AgentUsageAccountingService.java"
RAGENT_AGENT_COST_PROPERTIES="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/usage/AgentCostProperties.java"
RAGENT_TRUSTED_GENERATOR="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/TrustedAnswerGenerator.java"
RAGENT_EMBEDDING_CACHE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/EmbeddingVectorCache.java"
RAGENT_CHUNK_EMBEDDING="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/ChunkEmbeddingService.java"
RAGENT_CHUNK_METRICS="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/ChunkEmbeddingMetrics.java"
RAGENT_RAG_METRICS="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/observability/RagMetrics.java"
RAGENT_TRACE_FILTER="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/observability/CorrelationTraceFilter.java"
RAGENT_MODEL_METRICS="${RAGENT_PROJECT_DIR}/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelCallMetrics.java"
RAGENT_RETRIEVAL_CACHE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/cache/TrustedRetrievalCache.java"
RAGENT_RETRIEVAL_CACHE_PROPERTIES="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/cache/RetrievalCacheProperties.java"
RAGENT_RETRIEVAL_VERSION="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/cache/RetrievalIndexVersionCoordinator.java"
RAGENT_RETRIEVAL_CACHE_REAL_TEST="${RAGENT_PROJECT_DIR}/bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/cache/TrustedRetrievalCacheRealIntegrationTest.java"
RAGENT_PG_VECTOR_STORE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/vector/PgVectorStoreService.java"
RAGENT_MILVUS_VECTOR_STORE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/vector/MilvusVectorStoreService.java"
RAGENT_GENERATION_SERVICE="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/generation/IndexGenerationService.java"
RAGENT_GENERATION_POLICY="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/generation/IndexGenerationPolicy.java"
RAGENT_GENERATION_REPOSITORY="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/generation/IndexGenerationRepository.java"
RAGENT_GENERATION_ADMIN="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/IndexGenerationAdminController.java"
RAGENT_GENERATION_UPGRADE="${RAGENT_PROJECT_DIR}/resources/database/upgrade_index_generation.sql"
RAGENT_AGENT_PROMPT="${RAGENT_PROJECT_DIR}/bootstrap/src/main/resources/prompt/agent-planner.st"
RAGENT_ARTICLE_CLIENT="${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/agent/HttpPaicodingArticleClient.java"
PAICODING_ARTICLE_DAO="${ROOT_DIR}/paicoding-service/src/main/java/com/github/paicoding/forum/service/article/repository/dao/ArticleDao.java"
PAICODING_ARTICLE_INTERNAL="${ROOT_DIR}/paicoding-web/src/main/java/com/github/paicoding/forum/web/controller/ai/internal/AiKnowledgeArticleInternalRestController.java"
PAICODING_KNOWLEDGE_METRICS="${ROOT_DIR}/paicoding-service/src/main/java/com/github/paicoding/forum/service/ai/index/ArticleKnowledgeMetrics.java"
PAICODING_OUTBOX_DAO="${ROOT_DIR}/paicoding-service/src/main/java/com/github/paicoding/forum/service/notify/repository/dao/MqOutboxEventDao.java"
PAICODING_WEB_FILTER="${ROOT_DIR}/paicoding-web/src/main/java/com/github/paicoding/forum/web/hook/filter/ReqRecordFilter.java"
PAICODING_KNOWLEDGE_EVENT="${ROOT_DIR}/paicoding-api/src/main/java/com/github/paicoding/forum/api/model/event/ArticleKnowledgeEvent.java"
PAICODING_KNOWLEDGE_CONSUMER="${ROOT_DIR}/paicoding-aigc-service/src/main/java/com/github/paicoding/forum/aigc/mq/ArticleKnowledgeRocketMqConsumer.java"
PAICODING_RAGENT_SYNC="${ROOT_DIR}/paicoding-service/src/main/java/com/github/paicoding/forum/service/ai/service/impl/RagentKnowledgeSyncServiceImpl.java"
PAICODING_GENERATION_REBUILD="${ROOT_DIR}/paicoding-service/src/main/java/com/github/paicoding/forum/service/ai/index/ArticleKnowledgeGenerationRebuildService.java"
PAICODING_GENERATION_TASK="${ROOT_DIR}/paicoding-service/src/main/java/com/github/paicoding/forum/service/ai/index/ArticleKnowledgeGenerationRebuildTaskService.java"
PAICODING_GENERATION_ADMIN="${ROOT_DIR}/paicoding-web/src/main/java/com/github/paicoding/forum/web/admin/rest/AiKnowledgeGenerationAdminRestController.java"
PAICODING_AI_DEV_CONFIG="${ROOT_DIR}/paicoding-web/src/main/resources-env/dev/application-ai.yml"
rg -q 'port:[[:space:]]*9090' "${RAGENT_APPLICATION}" || fail "Ragent port contract"
rg -q 'context-path:[[:space:]]*/api/ragent' "${RAGENT_APPLICATION}" || fail "Ragent context path contract"
rg -q 'token-name:[[:space:]]*Authorization' "${RAGENT_APPLICATION}" || fail "Ragent Sa-Token header contract"
rg -q 'headers\.common\.Authorization = token' "${RAGENT_FRONTEND_API}" || fail "Ragent raw token frontend contract"
echo "[OK] associated Ragent HTTP and authentication contract"

rg -q 'ReciprocalRankFusion' "${RAGENT_HYBRID_RETRIEVER}" \
  || fail "Ragent hybrid retrieval RRF contract"
rg -q 'retrieval channel degraded' "${RAGENT_HYBRID_RETRIEVER}" \
  || fail "Ragent hybrid retrieval degradation contract"
rg -q 'jsonb_extract_path_text\(metadata, \?\) = \?' "${RAGENT_METADATA_FILTERS}" \
  || fail "Ragent metadata filter parameter binding contract"
rg -q 'ALLOWED_KEYS' "${RAGENT_METADATA_FILTERS}" \
  || fail "Ragent metadata filter allowlist contract"
rg -q 'MAX_CHUNKS_PER_ARTICLE[[:space:]]*=[[:space:]]*2' "${RAGENT_DIVERSITY_POLICY}" \
  || fail "Ragent retrieval same-article diversity boundary"
rg -q 'diversityPolicy\.select' "${RAGENT_HYBRID_RETRIEVER}" \
  || fail "Ragent hybrid retrieval diversity application"
echo "[OK] associated Ragent hybrid retrieval safety boundary"

rg -q 'Semaphore' "${RAGENT_EMBEDDING_GOVERNOR}" \
  || fail "Ragent embedding concurrency boundary"
rg -q 'tryConsumeRateToken' "${RAGENT_EMBEDDING_GOVERNOR}" \
  || fail "Ragent embedding local rate boundary"
rg -q 'isRetryable' "${RAGENT_EMBEDDING_GOVERNOR}" \
  || fail "Ragent embedding selective retry boundary"
[[ -r "${RAGENT_HTTP_CONFIG}" ]] \
  || fail "Ragent model HTTP configuration is missing"
! rg -q 'Duration\.ZERO' "${RAGENT_HTTP_CONFIG}" \
  || fail "Ragent model HTTP timeout must be bounded"
rg -q 'query-timeout:[[:space:]]*10s' "${RAGENT_APPLICATION}" \
  || fail "Ragent PostgreSQL query timeout boundary"
rg -q 'socketTimeout=10' "${RAGENT_APPLICATION}" \
  || fail "Ragent PostgreSQL socket timeout boundary"
echo "[OK] associated Ragent embedding and retrieval timeout governance"

rg -q 'historyKeepTurns.*\* 2|maxTurns \* 2' "${RAGENT_MEMORY_STORE}" \
  || fail "Ragent conversation recent-window boundary"
rg -q 'listMessagesBetweenIds' "${RAGENT_MEMORY_SUMMARY}" \
  || fail "Ragent incremental conversation summary watermark boundary"
rg -q 'SUMMARY_LOCK_PREFIX' "${RAGENT_MEMORY_SUMMARY}" \
  || fail "Ragent conversation summary distributed-lock boundary"
rg -q 'conversationMemoryReadExecutor' "${RAGENT_MEMORY_SERVICE}" \
  || fail "Ragent conversation memory dedicated executor wiring"
rg -q 'new LinkedBlockingQueue<>\(32\)' "${RAGENT_THREAD_POOLS}" \
  || fail "Ragent conversation memory bounded queue"
rg -q 'conversation_memory_read_executor_' "${RAGENT_THREAD_POOLS}" \
  || fail "Ragent conversation memory thread isolation"
echo "[OK] durable conversation history, recent-window and incremental-summary boundary"

rg -q 'Semaphore' "${RAGENT_MODEL_CALL_GOVERNOR}" \
  || fail "Ragent Chat/Rerank concurrency bulkhead"
rg -q 'ModelCapability.CHAT' "${RAGENT_ROUTING_LLM}" \
  || fail "Ragent LLM concurrency governor wiring"
rg -q 'lease.close' "${RAGENT_ROUTING_LLM}" \
  || fail "Ragent streaming LLM permit cleanup"
rg -q 'ModelCapability.RERANK' "${RAGENT_ROUTING_RERANK}" \
  || fail "Ragent Rerank concurrency governor wiring"
rg -q 'chat-max-concurrent:[[:space:]]*4' "${RAGENT_APPLICATION}" \
  || fail "Ragent Chat concurrency configuration"
rg -q 'rerank-max-concurrent:[[:space:]]*4' "${RAGENT_APPLICATION}" \
  || fail "Ragent Rerank concurrency configuration"
echo "[OK] isolated Embedding, Rerank and LLM concurrency boundaries"

rg -q 'formatUntrustedContext' "${RAGENT_TRUSTED_RETRIEVAL}" \
  || fail "Ragent untrusted retrieval context boundary"
rg -q 'filters\.put\("sourceType", "ARTICLE"\)' "${RAGENT_TRUSTED_RETRIEVAL}" \
  || fail "Ragent trusted retrieval source type enforcement"
rg -q 'filters\.put\("status", "ONLINE"\)' "${RAGENT_TRUSTED_RETRIEVAL}" \
  || fail "Ragent trusted retrieval online enforcement"
rg -q 'RRF 分数只负责排序，不作为固定拒答阈值' "${RAGENT_EVIDENCE_POLICY}" \
  || fail "Ragent composite refusal decision boundary"
rg -q 'completeCitationCount' "${RAGENT_EVIDENCE_POLICY}" \
  || fail "Ragent citation completeness signal"
rg -q '<untrusted_documents>' "${RAGENT_TRUSTED_PROMPT}" \
  || fail "Ragent untrusted document prompt boundary"
rg -q '\[ref:chunkId\]' "${RAGENT_TRUSTED_PROMPT}" \
  || fail "Ragent answer citation format contract"
rg -q 'unknownChunkIds' "${RAGENT_CITATION_VALIDATOR}" \
  || fail "Ragent generated citation allowlist validation"
echo "[OK] associated Ragent trusted retrieval and evidence boundary"

rg -q 'retrievalCache\.lookup' "${RAGENT_TRUSTED_RETRIEVAL}" \
  || fail "Ragent trusted retrieval cache lookup"
rg -q 'retrievalCache\.put' "${RAGENT_TRUSTED_RETRIEVAL}" \
  || fail "Ragent trusted retrieval cache write"
for segment in queryHash filterIdentity getEmbeddingModelVersion getRerankerModelVersion generation; do
  rg -q "${segment}" "${RAGENT_RETRIEVAL_CACHE}" \
    || fail "Ragent retrieval cache key dimension is missing: ${segment}"
done
rg -q "redis.call\('INCR', KEYS\[1\]\)" "${RAGENT_RETRIEVAL_VERSION}" \
  || fail "Ragent retrieval cache Generation increment"
rg -q "redis.call\('INCR', KEYS\[2\]\)" "${RAGENT_RETRIEVAL_VERSION}" \
  || fail "Ragent retrieval cache active mutation counter"
rg -q 'safeToCache' "${RAGENT_RETRIEVAL_CACHE}" \
  || fail "Ragent retrieval cache mutation bypass"
rg -q 'isMutationGuardLongEnough' "${RAGENT_RETRIEVAL_CACHE_PROPERTIES}" \
  || fail "Ragent retrieval cache crash-guard TTL boundary"
rg -q '@Configuration' "${RAGENT_RETRIEVAL_CACHE_PROPERTIES}" \
  || fail "Ragent retrieval cache properties bean registration"
for store in "${RAGENT_PG_VECTOR_STORE}" "${RAGENT_MILVUS_VECTOR_STORE}"; do
  rg -q 'beginMutation' "${store}" || fail "Ragent vector mutation begin barrier: ${store}"
  rg -q 'completeMutation' "${store}" || fail "Ragent vector mutation completion barrier: ${store}"
  rg -q 'abortMutation' "${store}" || fail "Ragent vector mutation abort barrier: ${store}"
done
rg -q 'ttl-seconds:[[:space:]]*60' "${RAGENT_APPLICATION}" \
  || fail "Ragent retrieval cache bounded TTL"
rg -q 'mutation-guard-ttl-seconds:[[:space:]]*120' "${RAGENT_APPLICATION}" \
  || fail "Ragent retrieval cache mutation guard TTL"
rg -q '@EnabledIfEnvironmentVariable\(named = "RAGENT_REAL_REDIS_RETRIEVAL_CACHE"' \
  "${RAGENT_RETRIEVAL_CACHE_REAL_TEST}" \
  || fail "Ragent real Redis retrieval cache safety gate"
rg -q 'RAGENT_RETRIEVAL_CACHE_TIMEOUT_SECONDS:-180' \
  "${ROOT_DIR}/scripts/run-retrieval-cache-real-validation.sh" \
  || fail "Ragent real Redis retrieval cache total timeout"
rg -q 'assertEquals\(2L, deleted\)' "${RAGENT_RETRIEVAL_CACHE_REAL_TEST}" \
  || fail "Ragent real Redis retrieval cache cleanup evidence"
echo "[OK] versioned retrieval cache and index-mutation invalidation boundary"

[[ -r "${RAGENT_GENERATION_UPGRADE}" ]] || fail "Ragent index Generation upgrade SQL is missing"
rg -q 'PRIMARY KEY \(collection_name, id\)' "${RAGENT_PROJECT_DIR}/resources/database/schema_pg.sql" \
  || fail "Ragent vector identity must include physical Generation"
rg -q 'CREATE TABLE t_index_generation' "${RAGENT_PROJECT_DIR}/resources/database/schema_pg.sql" \
  || fail "Ragent index Generation state table is missing"
rg -q 'CREATE TABLE t_vector_document_identity' "${RAGENT_PROJECT_DIR}/resources/database/schema_pg.sql" \
  || fail "Ragent cross-Generation document identity table is missing"
rg -q 'CREATE TABLE IF NOT EXISTS t_vector_document_identity' "${RAGENT_GENERATION_UPGRADE}" \
  || fail "Ragent document identity migration is missing"
rg -q 'findForUpdate' "${RAGENT_GENERATION_SERVICE}" \
  || fail "Ragent Generation state transition must lock its control row"
rg -q 'appliedWatermark.*targetWatermark' "${RAGENT_GENERATION_POLICY}" \
  || fail "Ragent Generation catch-up watermark boundary"
rg -q 'current\.status\(\) != IndexGenerationStatus\.READY' "${RAGENT_GENERATION_POLICY}" \
  || fail "Ragent Generation activation readiness boundary"
rg -q 'writeCollections' "${RAGENT_PG_VECTOR_STORE}" \
  || fail "Ragent incremental writes must route to rebuilding Generation"
rg -q "metadata->>'articleVersion'.*::bigint <= \?" "${RAGENT_PG_VECTOR_STORE}" \
  || fail "Ragent article-aware delete must remove only same-or-older business versions"
rg -q 't_vector_document_identity' "${RAGENT_PG_VECTOR_STORE}" \
  || fail "Ragent document lifecycle must retain cross-docId business identity"
rg -q 'MIN.*articleVersion.*min_version' "${RAGENT_GENERATION_REPOSITORY}" \
  || fail "Ragent reconciliation must detect stale article versions"
rg -q 'MAX.*articleVersion.*max_version' "${RAGENT_GENERATION_REPOSITORY}" \
  || fail "Ragent reconciliation must detect current article versions"
rg -q 'readCollection' "${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverService.java" \
  || fail "Ragent Dense retrieval must resolve the active Generation"
rg -q 'readCollection' "${RAGENT_PROJECT_DIR}/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgBm25KeywordRetriever.java" \
  || fail "Ragent keyword retrieval must resolve the active Generation"
rg -q 'StpUtil\.checkRole\("admin"\)' "${RAGENT_GENERATION_ADMIN}" \
  || fail "Ragent Generation control plane must require administrator role"
rg -Fq 'enabled: ${RAGENT_INDEX_GENERATION_ENABLED:false}' "${RAGENT_APPLICATION}" \
  || fail "Ragent Generation feature must remain disabled before migration"
echo "[OK] watermarked two-Generation rebuild and atomic alias boundary"

rg -q 'latestArticleKnowledgeWatermark' "${PAICODING_OUTBOX_DAO}" \
  || fail "PaiCoding Generation rebuild start/target Outbox watermark"
rg -q 'listArticleKnowledgeBetween' "${PAICODING_OUTBOX_DAO}" \
  || fail "PaiCoding bounded Generation Outbox catch-up query"
rg -q 'long startWatermark = outboxEventDao.latestArticleKnowledgeWatermark' "${PAICODING_GENERATION_REBUILD}" \
  || fail "PaiCoding snapshot must record its starting Outbox watermark"
rg -q 'replayTo.*appliedWatermark.*targetWatermark' "${PAICODING_GENERATION_REBUILD}" \
  || fail "PaiCoding Generation rebuild must replay bounded Outbox increments"
rg -q 'watermarkAfterReconcile == targetWatermark' "${PAICODING_GENERATION_REBUILD}" \
  || fail "PaiCoding Generation rebuild must recheck concurrent increments after reconciliation"
rg -q 'stable && consistent' "${PAICODING_GENERATION_REBUILD}" \
  || fail "PaiCoding Generation activation must require stable watermark and exact reconciliation"
rg -q 'ragent\.activateGeneration' "${PAICODING_GENERATION_REBUILD}" \
  || fail "PaiCoding Generation orchestrator activation command"
rg -q 'ragent\.failGeneration' "${PAICODING_GENERATION_REBUILD}" \
  || fail "PaiCoding Generation orchestrator fail-closed command"
rg -q 'newSingleThreadExecutor' "${PAICODING_GENERATION_TASK}" \
  || fail "PaiCoding Generation task must use a bounded single worker"
rg -q 'current\.status\(\)\.active' "${PAICODING_GENERATION_TASK}" \
  || fail "PaiCoding Generation task must reject concurrent local rebuilds"
rg -q 'GENERATION_LABEL\.matcher' "${PAICODING_GENERATION_TASK}" \
  || fail "PaiCoding Generation label must be validated before enqueue"
rg -q 'Generation重建失败，请查看服务端脱敏日志' "${PAICODING_GENERATION_TASK}" \
  || fail "PaiCoding Generation task status must not expose raw dependency errors"
! rg -q 'spock\.mockfree|com\.sayweee' "${ROOT_DIR}/paicoding-web/pom.xml" \
    "${ROOT_DIR}/paicoding-web/src/test" \
  || fail "legacy MockFree global attach listener must not re-enter the test runtime"
rg -q '^mock-maker-subclass$' \
  "${ROOT_DIR}/paicoding-web/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker" \
  || fail "PaiCoding tests must not require dynamic Java Agent attachment in WSL"
rg -q '@Permission\(role = UserRole\.ADMIN\)' "${PAICODING_GENERATION_ADMIN}" \
  || fail "PaiCoding Generation control plane must require administrator role"
for source in "${PAICODING_GENERATION_REBUILD}" "${PAICODING_GENERATION_TASK}" "${PAICODING_GENERATION_ADMIN}"; do
  rg -q '@ConditionalOnProperty\(name = "ai\.knowledge\.generation-rebuild\.enabled", havingValue = "true"\)' "${source}" \
    || fail "PaiCoding Generation component must be gated by the default-off feature: ${source}"
done
rg -q 'generationRebuild:' "${PAICODING_AI_DEV_CONFIG}" \
  || fail "PaiCoding Generation rebuild configuration block is missing"
rg -A6 'generationRebuild:' "${PAICODING_AI_DEV_CONFIG}" | rg -q 'enabled:[[:space:]]*false' \
  || fail "PaiCoding Generation rebuild must remain disabled by default"
echo "[OK] PaiCoding snapshot, Outbox catch-up, reconciliation and async-admin boundary"

for tool in SEARCH_KNOWLEDGE GET_ARTICLE_DETAIL SEARCH_RELATED_ARTICLES GET_CONVERSATION_SUMMARY; do
  rg -q "${tool}" "${RAGENT_AGENT_TOOLS}" || fail "Ragent Agent tool allowlist is incomplete: ${tool}"
done
rg -q 'MAX_STEPS[[:space:]]*=[[:space:]]*3' "${RAGENT_AGENT_BUDGET}" \
  || fail "Ragent Agent maximum step boundary"
rg -q 'MAX_RETRIEVAL_CALLS[[:space:]]*=[[:space:]]*2' "${RAGENT_AGENT_BUDGET}" \
  || fail "Ragent Agent retrieval call boundary"
rg -q 'MAX_TOKEN_BUDGET[[:space:]]*=[[:space:]]*8_000' "${RAGENT_AGENT_BUDGET}" \
  || fail "Ragent Agent token boundary"
rg -q 'MAX_TIMEOUT_MILLIS[[:space:]]*=[[:space:]]*15_000' "${RAGENT_AGENT_BUDGET}" \
  || fail "Ragent Agent deadline boundary"
rg -q 'AGENT_DUPLICATE_TOOL_CALL' "${RAGENT_AGENT_BUDGET}" \
  || fail "Ragent Agent duplicate-loop boundary"
rg -q 'SynchronousQueue' "${RAGENT_AGENT_POOL}" \
  || fail "Ragent Agent executor must reject queue accumulation"
rg -q 'AGENT_DETAIL_NOT_DISCOVERED' "${RAGENT_AGENT_EXECUTOR}" \
  || fail "Ragent Agent article detail discovery boundary"
rg -q 'new AgentToolContext\(UserContext\.getUserId\(\)\)' "${RAGENT_AGENT_EXECUTOR}" \
  || fail "Ragent Agent explicit identity propagation boundary"
rg -q '@ConditionalOnProperty\(name = "rag.agent.enabled", havingValue = "true"\)' \
  "${RAGENT_AGENT_CONTROLLER}" || fail "Ragent Agent endpoint default-off gate"
rg -q 'enabled:[[:space:]]*false' "${RAGENT_APPLICATION}" \
  || fail "Ragent Agent must be disabled by default"
rg -q 'UserContext\.requireUser' "${RAGENT_AGENT_CONTROLLER}" \
  || fail "Ragent Agent endpoint login boundary"
! rg -q 'fallbackRetrieval' "${RAGENT_AGENT_CONTROLLER}" \
  || fail "Ragent Agent response must not expose fallback document context"
rg -q '不要 reasoning、thought、plan 字段' "${RAGENT_AGENT_PROMPT}" \
  || fail "Ragent Agent planner thought-output boundary"
rg -q 'MAX_RESPONSE_BYTES' "${RAGENT_ARTICLE_CLIENT}" \
  || fail "Ragent article fact-source response size boundary"
rg -q 'token == null \|\| token\.isBlank' "${RAGENT_ARTICLE_CLIENT}" \
  || fail "Ragent article fact-source internal token boundary"
rg -q 'ArticleDO::getStatus, PushStatusEnum\.ONLINE' "${PAICODING_ARTICLE_DAO}" \
  || fail "PaiCoding Agent article fact source ONLINE boundary"
rg -q 'ArticleDO::getDeleted, YesOrNoEnum\.NO' "${PAICODING_ARTICLE_DAO}" \
  || fail "PaiCoding Agent article fact source deletion boundary"
rg -q 'accessValidator\.validate' "${PAICODING_ARTICLE_INTERNAL}" \
  || fail "PaiCoding Agent article fact source authentication boundary"
[[ -r "${RAGENT_PROJECT_DIR}/bootstrap/src/test/resources/rag/agent-fixed-tasks.tsv" ]] \
  || fail "Ragent fixed Agent control-flow task set"
rg -q 'ControlledAgentFixedTaskSetTest' "${ROOT_DIR}/scripts/run-agent-control-flow-evaluation.sh" \
  || fail "Ragent fixed Agent control-flow evaluation entry"
echo "[OK] controlled single-Agent and article fact-source safety boundary"

rg -Fq 'HASH_TAG = "{agent-quota}"' "${RAGENT_AGENT_QUOTA}" \
  || fail "Ragent Agent quota Redis Cluster hash-tag boundary"
rg -q "redis.call\('INCRBY'" "${RAGENT_AGENT_QUOTA}" \
  || fail "Ragent Agent quota atomic reservation boundary"
rg -q "redis.call\('DECRBY'" "${RAGENT_AGENT_QUOTA}" \
  || fail "Ragent Agent quota unused-budget refund boundary"
rg -q "'ACTIVE'" "${RAGENT_AGENT_QUOTA}" \
  || fail "Ragent Agent quota active reservation marker"
rg -q "'SETTLED'" "${RAGENT_AGENT_QUOTA}" \
  || fail "Ragent Agent quota idempotent settlement marker"
rg -q 'Agent配额服务不可用，已拒绝本次请求' "${RAGENT_AGENT_QUOTA}" \
  || fail "Ragent Agent quota must fail closed when Redis is unavailable"
rg -q 'quotaService\.reserve' "${RAGENT_AGENT_CONTROLLER}" \
  || fail "Ragent Agent quota reservation before execution"
rg -q 'finally' "${RAGENT_AGENT_CONTROLLER}" \
  || fail "Ragent Agent quota settlement cleanup boundary"
rg -q 'quotaService\.settle' "${RAGENT_AGENT_CONTROLLER}" \
  || fail "Ragent Agent quota actual-usage settlement"
rg -q 'SAFE_SESSION_ID' "${RAGENT_AGENT_CONTROLLER}" \
  || fail "Ragent Agent bounded session identity boundary"
rg -q 'userDailySteps[[:space:]]*=[[:space:]]*60' "${RAGENT_AGENT_QUOTA_PROPERTIES}" \
  || fail "Ragent Agent default user daily step quota"
rg -q 'sessionDailyTokens[[:space:]]*=[[:space:]]*48_000' "${RAGENT_AGENT_QUOTA_PROPERTIES}" \
  || fail "Ragent Agent default session daily token quota"
rg -q 'quota:' "${RAGENT_APPLICATION}" \
  || fail "Ragent Agent quota configuration block"
rg -q 'user-daily-steps:[[:space:]]*60' "${RAGENT_APPLICATION}" \
  || fail "Ragent Agent user daily step quota configuration"
rg -q 'session-daily-tokens:[[:space:]]*48000' "${RAGENT_APPLICATION}" \
  || fail "Ragent Agent session daily token quota configuration"
rg -q '@EnabledIfEnvironmentVariable\(named = "RAGENT_REAL_REDIS_QUOTA"' \
  "${RAGENT_AGENT_QUOTA_REAL_TEST}" \
  || fail "Ragent Agent real Redis quota test safety gate"
rg -q 'RAGENT_AGENT_QUOTA_TIMEOUT_SECONDS:-180' \
  "${ROOT_DIR}/scripts/run-agent-quota-real-validation.sh" \
  || fail "Ragent Agent real Redis quota test total timeout"
rg -q 'assertEquals\(5L, deleted\)' "${RAGENT_AGENT_QUOTA_REAL_TEST}" \
  || fail "Ragent Agent real Redis quota key cleanup evidence"
echo "[OK] controlled single-Agent Redis quota and settlement boundary"

for counter in toolCalls fallbackRetrievalCalls rerankCalls modelCalls; do
  rg -q "${counter}" "${RAGENT_AGENT_BUDGET}" \
    || fail "Ragent Agent request usage counter is missing: ${counter}"
done
rg -q 'modelCalled' "${RAGENT_TRUSTED_GENERATOR}" \
  || fail "Ragent Agent generation model-call accounting boundary"
rg -q 'estimatedCostMicros' "${RAGENT_AGENT_USAGE}" \
  || fail "Ragent Agent estimated cost accounting"
rg -q 'totalRetrievalCalls' "${RAGENT_AGENT_USAGE}" \
  || fail "Ragent Agent total retrieval accounting"
! rg -q 'String[[:space:]]+(userId|sessionId|query)' "${RAGENT_AGENT_USAGE}" \
  || fail "Ragent Agent usage report must not accept high-cardinality identity or query fields"
rg -q 'estimatedMicrosPerMillionTokens' "${RAGENT_AGENT_COST_PROPERTIES}" \
  || fail "Ragent Agent configurable model price boundary"
rg -q '@Configuration' "${RAGENT_AGENT_COST_PROPERTIES}" \
  || fail "Ragent Agent cost properties bean registration"
rg -q 'estimated-micros-per-million-tokens:.*RAGENT_AGENT_COST_MICROS_PER_MILLION_TOKENS:0' \
  "${RAGENT_APPLICATION}" \
  || fail "Ragent Agent cost must remain unconfigured until an explicit model price is supplied"
rg -q 'usageAccountingService\.summarize' "${RAGENT_AGENT_CONTROLLER}" \
  || fail "Ragent Agent safe request usage response"
echo "[OK] controlled single-Agent request usage and estimated-cost boundary"

rg -q "return modelId \+ ':' \+ contentIdentity" "${RAGENT_CHUNK_EMBEDDING}" \
  || fail "Ragent Embedding cache key must contain model and content identity"
rg -q 'metadata\(\)\.get\("contentHash"\)|getMetadata\(\).*get\("contentHash"\)' \
  "${RAGENT_CHUNK_EMBEDDING}" \
  || fail "Ragent Embedding cache contentHash boundary"
rg -q 'DEFAULT_MAX_ENTRIES[[:space:]]*=[[:space:]]*10_000' "${RAGENT_EMBEDDING_CACHE}" \
  || fail "Ragent Embedding cache bounded size"
rg -q 'removeEldestEntry' "${RAGENT_EMBEDDING_CACHE}" \
  || fail "Ragent Embedding cache LRU eviction"
echo "[OK] bounded model-version and contentHash Embedding cache boundary"

rg -q 'spring-boot-starter-actuator' "${RAGENT_PROJECT_DIR}/bootstrap/pom.xml" \
  || fail "Ragent Actuator dependency"
rg -q 'micrometer-registry-prometheus' "${RAGENT_PROJECT_DIR}/bootstrap/pom.xml" \
  || fail "Ragent Prometheus registry dependency"
rg -q 'include:[[:space:]]*health,info,prometheus' "${RAGENT_APPLICATION}" \
  || fail "Ragent Prometheus endpoint exposure"
for metric in rag.model.calls.inflight rag.model.calls.rejected rag.model.calls.retried rag.model.calls.duration; do
  rg -Fq "${metric}" "${RAGENT_MODEL_METRICS}" \
    || fail "Ragent model metric is missing: ${metric}"
done
for metric in rag.embedding.chunks rag.embedding.batch.duration rag.embedding.batches; do
  rg -Fq "${metric}" "${RAGENT_CHUNK_METRICS}" \
    || fail "Ragent Chunk/Embedding metric is missing: ${metric}"
done
for metric in rag.retrieval.stage.duration rag.retrieval.results rag.retrieval.degraded \
    rag.retrieval.requests rag.retrieval.citations rag.agent.requests rag.agent.terminations \
    rag.agent.steps rag.agent.tool.calls rag.agent.retrieval.calls rag.agent.model.calls rag.agent.tokens; do
  rg -Fq "${metric}" "${RAGENT_RAG_METRICS}" \
    || fail "Ragent retrieval/Agent metric is missing: ${metric}"
done
for metric in rag.index.outbox.events rag.index.events rag.index.sync.latency rag.index.processing.duration; do
  rg -Fq "${metric}" "${PAICODING_KNOWLEDGE_METRICS}" \
    || fail "PaiCoding knowledge-index metric is missing: ${metric}"
done
rg -q 'refresh-ms:15000' "${PAICODING_KNOWLEDGE_METRICS}" \
  || fail "PaiCoding knowledge-index metric refresh boundary"
rg -q 'countByStatusForTag' "${PAICODING_OUTBOX_DAO}" \
  || fail "PaiCoding knowledge Outbox tag-scoped gauge query"
for source in "${RAGENT_MODEL_METRICS}" "${RAGENT_CHUNK_METRICS}" \
    "${RAGENT_RAG_METRICS}" "${PAICODING_KNOWLEDGE_METRICS}"; do
  ! rg -q '\.tag\("(userId|articleId|eventId|query|queryHash|conversationId)"' "${source}" \
    || fail "high-cardinality Prometheus label detected: ${source}"
done
echo "[OK] low-cardinality RAG, Agent, model and knowledge-index metrics"

rg -q 'TRACE_ID_HEADER[[:space:]]*=[[:space:]]*"X-Trace-Id"' "${PAICODING_WEB_FILTER}" \
  || fail "PaiCoding must accept the Gateway correlation header"
rg -q 'private String traceId' "${PAICODING_KNOWLEDGE_EVENT}" \
  || fail "knowledge event correlation field"
rg -q 'event\.getTraceId' "${PAICODING_KNOWLEDGE_CONSUMER}" \
  || fail "knowledge consumer correlation restoration"
rg -q 'SAFE_TRACE_ID\.matcher\(traceId\)\.matches' \
  "${ROOT_DIR}/paicoding-core/src/main/java/com/github/paicoding/forum/core/mdc/MdcUtil.java" \
  || fail "MQ/manual correlation ID must pass the central allowlist"
rg -q 'previousMdc' "${PAICODING_KNOWLEDGE_CONSUMER}" \
  || fail "knowledge consumer MDC cleanup boundary"
rg -q 'headers\.set\("X-Trace-Id", traceId\)' "${PAICODING_RAGENT_SYNC}" \
  || fail "PaiCoding to Ragent correlation propagation"
rg -q 'class CorrelationTraceFilter' "${RAGENT_TRACE_FILTER}" \
  || fail "Ragent inbound correlation filter"
rg -q 'Pattern\.compile\("\[A-Za-z0-9\._-\]\{8,64\}"\)' "${RAGENT_TRACE_FILTER}" \
  || fail "Ragent inbound correlation allowlist"
rg -q '<skywalking-toolkit.version>9\.6\.0</skywalking-toolkit.version>' "${RAGENT_POM}" \
  || fail "Ragent SkyWalking toolkit must align with Java Agent 9.6.0"
for operation in rag.retrieval.hybrid rag.retrieval.trusted rag.model.rerank rag.model.chat \
    rag.model.stream rag.agent.execute rag.agent.tool.search_knowledge \
    rag.agent.tool.search_related_articles rag.agent.tool.get_article_detail \
    rag.agent.tool.get_conversation_summary; do
  rg -q "@Trace\(operationName = \"${operation}\"\)" "${RAGENT_PROJECT_DIR}" \
    || fail "Ragent SkyWalking Span is missing: ${operation}"
done
for operation in rag.index.rocketmq.consume rag.index.converge rag.index.sync_to_ragent; do
  rg -q "@Trace\(operationName = \"${operation}\"\)" "${ROOT_DIR}" \
    || fail "PaiCoding knowledge SkyWalking Span is missing: ${operation}"
done
! rg -q '@Tag\([^\n]*(userId|articleId|eventId|query|conversationId|prompt)' \
    "${RAGENT_PROJECT_DIR}/bootstrap/src/main/java" "${RAGENT_PROJECT_DIR}/infra-ai/src/main/java" \
    "${PAICODING_KNOWLEDGE_CONSUMER}" "${PAICODING_RAGENT_SYNC}" \
  || fail "high-cardinality or sensitive SkyWalking Span tag detected"
echo "[OK] Gateway, RocketMQ, Ragent correlation and fixed-name SkyWalking Span boundary"

rg -q 'validation-embedding-1536' "${RAGENT_APPLICATION}" \
  || fail "Ragent deterministic validation model candidate"
rg -q 'RAGENT_VALIDATION_EMBEDDING_ENABLED:false' "${RAGENT_APPLICATION}" \
  || fail "Ragent validation embedding must be disabled by default"
rg -q '@ConditionalOnProperty\(name = "ai.validation.embedding.enabled", havingValue = "true"\)' \
  "${RAGENT_VALIDATION_CLIENT}" \
  || fail "Ragent validation embedding conditional registration"
rg -q '不表达文本语义|不能用于检索质量' "${RAGENT_VALIDATION_CLIENT}" \
  || fail "Ragent validation embedding evidence warning"
rg -q 'semantic_quality=NOT_VALIDATED' \
  "${ROOT_DIR}/scripts/run-ragent-integration-validation-worker.sh" \
  || fail "validation worker semantic evidence marker"
rg -q 'BOOT-INF/lib/infra-ai-0\.0\.1-SNAPSHOT\.jar' \
  "${ROOT_DIR}/scripts/run-ragent-integration-validation-worker.sh" \
  || fail "validation worker nested infra-ai jar freshness check"
echo "[OK] deterministic validation embedding safety boundary"

for property in maven-compiler-plugin.version maven-dependency-plugin.version \
    maven-resources-plugin.version maven-surefire-plugin.version grpc.version; do
  rg -q "<${property}>[^<]+</${property}>" "${RAGENT_POM}" \
    || fail "Ragent Maven plugin version is not pinned: ${property}"
done
rg -q 'mvn -nsu' "${ROOT_DIR}/scripts/build-ragent-integration-artifact.sh" \
  || fail "Ragent bounded build must suppress snapshot metadata updates"
rg -q 'spotless\.apply\.skip=true' "${ROOT_DIR}/scripts/build-ragent-integration-artifact.sh" \
  || fail "Ragent bounded build must not rewrite source files"
echo "[OK] associated Ragent reproducible Maven build boundary"

rg -q 'RAGENT_REAL_INTEGRATION.*true' \
  "${ROOT_DIR}/scripts/run-ragent-integration-validation-worker.sh" \
  || fail "real integration explicit switch"
rg -q '@EnabledIfEnvironmentVariable\(named = "RAGENT_REAL_INTEGRATION"' \
  "${ROOT_DIR}/paicoding-web/src/test/java/com/github/paicoding/forum/service/ai/RagentKnowledgeSyncRealIntegrationTest.java" \
  || fail "real integration test safety gate"
echo "[OK] real dependency test safety gate"

FULL_CHAIN_TEST="${ROOT_DIR}/paicoding-web/src/test/java/com/github/paicoding/forum/service/ai/ArticleKnowledgeFullChainRealIntegrationTest.java"
FULL_CHAIN_WORKER="${ROOT_DIR}/scripts/run-article-knowledge-full-chain-worker.sh"
AIGC_APPLICATION="${ROOT_DIR}/paicoding-aigc-service/src/main/resources/application.yml"
rg -q '@EnabledIfSystemProperty\(named = "article\.knowledge\.full-chain\.integration\.enabled"' \
  "${FULL_CHAIN_TEST}" || fail "full-chain test safety gate"
rg -q -- '-Darticle\.knowledge\.full-chain\.integration\.enabled=true' \
  "${FULL_CHAIN_WORKER}" || fail "full-chain worker explicit test switch"
rg -q 'ARTICLE_KNOWLEDGE_FULL_CHAIN_TIMEOUT_SECONDS:-900' \
  "${FULL_CHAIN_WORKER}" || fail "full-chain worker total timeout"
rg -q 'trap finish EXIT' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain worker cleanup trap"
rg -q 'generate-article-knowledge-full-chain-report.sh' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain worker automatic report generation"
rg -q 'nohup bash.*WORKER_SCRIPT' "${ROOT_DIR}/scripts/submit-article-knowledge-full-chain.sh" \
  || fail "full-chain fast background submission"
rg -q 'ps -p.*WORKER_SCRIPT' "${ROOT_DIR}/scripts/stop-article-knowledge-full-chain.sh" \
  || fail "full-chain safe worker identity check before stop"
rg -q 'NOT_VERIFIED' "${ROOT_DIR}/scripts/generate-article-knowledge-full-chain-report.sh" \
  || fail "full-chain report must preserve missing evidence"
rg -q "result=DUPLICATE" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain duplicate-event evidence gate"
rg -q "result=STALE" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain stale-event evidence gate"
rg -q 'version=4, result=FAILED' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain failed-attempt evidence gate"
rg -q 'version=4, result=APPLIED' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain retry-recovery evidence gate"
rg -q "metadata->>'articleId'" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain article metadata evidence gate"
rg -q "metadata->>'headingPath'" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain heading path evidence gate"
rg -q "metadata->>'contentHash'.*64" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain content hash evidence gate"
rg -q "id.*20" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain stable chunk ID evidence gate"
rg -q "metadata->>'tokenCount'" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain token count evidence gate"
rg -q "metadata->>'overlapTokenCount'" "${FULL_CHAIN_WORKER}" \
  || fail "full-chain token overlap evidence gate"
rg -q '/rag/retrieve/hybrid' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain hybrid retrieval evidence gate"
rg -q 'metadata_filter=VERIFIED' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain retrieval metadata filter evidence marker"
rg -q 'semantic_quality=NOT_VALIDATED' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain retrieval semantic evidence boundary"
rg -q '/rag/retrieve/trusted' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain trusted retrieval evidence gate"
rg -q 'citation_traceability=VERIFIED' "${FULL_CHAIN_WORKER}" \
  || fail "full-chain citation traceability evidence marker"
rg -q 'ModernBm25OfflineEvaluatorTest' \
  "${ROOT_DIR}/scripts/run-modern-rag-bm25-evaluation.sh" \
  || fail "modern BM25 frozen-dataset evaluation entry"
rg -q 'ArticleKnowledgeRocketMqConsumer=INFO' \
  "${ROOT_DIR}/scripts/run-aigc-knowledge-low-resource.sh" \
  || fail "full-chain consumer result evidence logging"
rg -q 'application-rocketmq\.yml' "${AIGC_APPLICATION}" \
  || fail "standalone AIGC RocketMQ configuration import"
echo "[OK] full article knowledge event-chain safety boundary"

echo "Ragent integration static checks passed; no service or container was started."
