/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Agent HTTP 端到端验证。只有显式设置 RAGENT_REAL_AGENT_E2E=true 才执行。
 *
 * <p>链路：HTTP 登录 -> Agent Planner(HY3) -> searchKnowledge ->
 * Qwen3 Embedding -> pgvector/BM25 -> Qwen3 Rerank -> HY3 最终回答。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "rag.agent.enabled=true",
        "rag.agent.quota.enabled=false",
        "rag.retrieval.cache.enabled=false",
        "rag.query-rewrite.enabled=false",
        "rag.trace.enabled=false",
        "rag.vector.type=pg",
        "spring.data.redis.port=16379",
        "spring.data.redis.password=123456",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.minimum-idle=1",
        "rocketmq.name-server=127.0.0.1:9876",
        "ai.chat.default-model=hy3-free",
        "ai.embedding.default-model=qwen-emb-8b",
        "ai.rerank.default-model=qwen3-rerank-siliconflow",
        "debug=false",
        "logging.level.root=WARN",
        "logging.level.com.nageoffer.ai.ragent=INFO",
        "logging.level.org.springframework=WARN"
})
@EnabledIfEnvironmentVariable(named = "RAGENT_REAL_AGENT_E2E", matches = "true")
class RealAgentEndToEndTest {
    private static final String COLLECTION = "rag_default_store";
    private static final String CHUNK_ID = "agent_e2e_20260720";
    private static final String ARTICLE_ID = "990020";
    private static final String MARKER = "PAI_AGENT_E2E_2026";
    private static final String QUESTION =
            "请检索社区知识库，并根据资料说明 PAI_AGENT_E2E_2026 中 Outbox 如何保证点赞通知可靠，回答必须引用资料。";
    private static final String CONTENT =
            "PAI_AGENT_E2E_2026 可靠消息规范：点赞状态只由 Redis 与 MySQL 维护。"
                    + "点赞持久化和通知 Outbox 记录在同一个 MySQL 本地事务中提交；事务回滚时二者一起回滚。"
                    + "独立投递器把 Outbox 事件发送到 RocketMQ，Broker 确认后才标记 SENT。"
                    + "发送失败使用指数退避重试，超过上限进入 DEAD 并允许人工修正重放。"
                    + "由于投递可能重复，消息服务按 eventId 幂等消费，并按 aggregateId 与 businessVersion 防止旧事件覆盖新状态。";

    @LocalServerPort
    private int port;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCompleteRealAgentHttpToolRetrievalAndAnswerChain() throws Exception {
        requireSecret("OPENROUTER_API_KEY");
        requireSecret("SILICONFLOW_API_KEY");
        long started = System.nanoTime();
        JsonNode responseData = null;
        Throwable failure = null;
        try {
            insertEvidence();
            String token = login();
            responseData = callAgent(token);
            assertSuccessfulAgent(responseData);
        } catch (Throwable caught) {
            failure = caught;
            throw caught;
        } finally {
            deleteEvidence();
            writeReport(responseData, failure, elapsedMillis(started));
        }
    }

    private void insertEvidence() throws Exception {
        List<Float> embedding = embeddingService.embed(CONTENT, "qwen-emb-8b");
        assertEquals(1536, embedding.size(), "Qwen3 Embedding 必须返回 pgvector 配置的 1536 维");
        String metadata = objectMapper.writeValueAsString(java.util.Map.of(
                "sourceType", "ARTICLE",
                "status", "ONLINE",
                "articleId", ARTICLE_ID,
                "articleVersion", "1",
                "title", "Agent真实端到端验证",
                "headingPath", "可靠消息/Outbox",
                "doc_id", "agent-e2e-doc"
        ));
        jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE collection_name = ? AND id = ?",
                COLLECTION, CHUNK_ID);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_vector (collection_name, id, content, metadata, embedding)
                VALUES (?, ?, ?, ?::jsonb, ?::vector)
                """, COLLECTION, CHUNK_ID, CONTENT, metadata, vectorLiteral(embedding));
    }

    private String login() throws Exception {
        JsonNode root = post("/auth/login", null, """
                {"username":"admin","password":"admin"}
                """);
        assertEquals("0", root.path("code").asText(), "真实 HTTP 登录失败");
        String token = root.path("data").path("token").asText();
        assertFalse(token.isBlank(), "登录响应没有 Token");
        return token;
    }

    private JsonNode callAgent(String token) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "question", QUESTION,
                "sessionId", "agent_e2e_20260720"
        ));
        JsonNode root = post("/rag/agent/query", token, body);
        assertEquals("0", root.path("code").asText(),
                () -> "Agent HTTP 请求失败，message=" + root.path("message").asText());
        return root.path("data");
    }

    private JsonNode post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/ragent" + path))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", token);
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .send(request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), "HTTP 状态异常");
        return objectMapper.readTree(response.body());
    }

    private void assertSuccessfulAgent(JsonNode data) {
        assertNotNull(data);
        assertEquals("AGENT", data.path("mode").asText(), "未走完 Agent 正常链路");
        assertFalse(data.path("fallback").asBoolean(true), "Agent 发生 RAG 降级");
        assertTrue(data.path("failureCode").asText().isBlank(), "Agent 返回失败码");
        String answer = data.path("answer").asText();
        assertFalse(answer.isBlank(), "Agent 最终回答为空");
        assertTrue(answer.contains("[ref:" + CHUNK_ID + "]"), "最终回答未引用隔离证据 Chunk");
        assertTrue(data.path("toolCalls").isArray());
        assertTrue(stream(data.path("toolCalls"))
                .anyMatch(item -> "searchKnowledge".equals(item.path("toolName").asText())
                        && "SUCCESS".equals(item.path("status").asText())),
                "未真实调用 searchKnowledge");
        assertTrue(stream(data.path("citations"))
                .anyMatch(item -> CHUNK_ID.equals(item.path("chunkId").asText())),
                "返回引用未命中隔离证据");
        JsonNode usage = data.path("usage");
        assertTrue(usage.path("agentRetrievalCalls").asInt() >= 1, "没有 Agent 检索调用计数");
        assertTrue(usage.path("rerankCalls").asInt() >= 1, "没有真实 Rerank 调用计数");
        assertTrue(usage.path("modelCalls").asInt() >= 2, "Planner/最终回答模型调用不足");
        assertEquals(0, usage.path("retrievalCacheHits").asInt(), "本次验证不允许命中检索缓存");
    }

    private java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        java.util.stream.Stream.Builder<JsonNode> builder = java.util.stream.Stream.builder();
        array.forEach(builder::add);
        return builder.build();
    }

    private String vectorLiteral(List<Float> vector) {
        StringBuilder value = new StringBuilder(vector.size() * 12).append('[');
        for (int index = 0; index < vector.size(); index++) {
            if (index > 0) value.append(',');
            value.append(vector.get(index));
        }
        return value.append(']').toString();
    }

    private void deleteEvidence() {
        try {
            jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE collection_name = ? AND id = ?",
                    COLLECTION, CHUNK_ID);
        } catch (RuntimeException ignored) {
            // 依赖容器由外层有界脚本最终清理；这里不掩盖原始验证结果。
        }
    }

    private void writeReport(JsonNode data, Throwable failure, long elapsedMillis) {
        String configured = System.getProperty("agent.e2e.report");
        if (configured == null || configured.isBlank()) return;
        Path report = Path.of(configured);
        String status = failure == null ? "通过" : "失败";
        String mode = data == null ? "" : data.path("mode").asText();
        JsonNode usage = data == null ? null : data.path("usage");
        String failureType = failure == null ? "" : failure.getClass().getSimpleName();
        String text = """
                # Agent 真实端到端验证报告

                生成时间：%s

                ## 结果

                - 状态：`%s`
                - 总耗时：`%d ms`
                - 响应模式：`%s`
                - 失败类型：`%s`
                - 测试证据已清理：`是`

                ## 实际验证链路

                ```text
                HTTP登录
                -> 受控Agent Planner（HY3）
                -> searchKnowledge只读工具
                -> Qwen3-Embedding-8B
                -> PostgreSQL pgvector + BM25
                -> Qwen3-Reranker-8B
                -> HY3带引用最终回答
                ```

                ## 低敏运行证据

                - Agent检索次数：`%d`
                - Rerank次数：`%d`
                - 模型调用次数：`%d`
                - 检索缓存命中：`%d`
                - 引用数量：`%d`
                - 工具调用数量：`%d`

                ## 边界

                - API Key、Prompt、模型原始输出、完整工具正文和用户Token均未写入报告。
                - 测试使用独立 Chunk，结束后按精确主键删除；依赖使用 tmpfs 并由外层脚本删除。
                - 通过只证明本次真实环境中的完整链路成立，不代表模型供应商永久可用。
                """.formatted(OffsetDateTime.now(), status, elapsedMillis, mode, failureType,
                intValue(usage, "agentRetrievalCalls"), intValue(usage, "rerankCalls"),
                intValue(usage, "modelCalls"), intValue(usage, "retrievalCacheHits"),
                data == null ? 0 : data.path("citations").size(),
                data == null ? 0 : data.path("toolCalls").size());
        try {
            Files.createDirectories(report.toAbsolutePath().getParent());
            Files.writeString(report, text, StandardCharsets.UTF_8);
        } catch (Exception reportFailure) {
            System.err.println("AGENT_E2E_REPORT_WRITE_FAILED type="
                    + reportFailure.getClass().getSimpleName());
        }
        System.out.printf(Locale.ROOT,
                "AGENT_REAL_E2E status=%s elapsed_ms=%d mode=%s model_calls=%d retrieval_calls=%d "
                        + "rerank_calls=%d citations=%d report=%s%n",
                status, elapsedMillis, mode, intValue(usage, "modelCalls"),
                intValue(usage, "agentRetrievalCalls"), intValue(usage, "rerankCalls"),
                data == null ? 0 : data.path("citations").size(), report.toAbsolutePath());
    }

    private int intValue(JsonNode node, String field) {
        return node == null ? 0 : node.path(field).asInt();
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private void requireSecret(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量 " + name);
    }
}
