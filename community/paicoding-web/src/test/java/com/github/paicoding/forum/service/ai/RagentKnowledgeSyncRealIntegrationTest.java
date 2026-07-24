package com.github.paicoding.forum.service.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.impl.RagentKnowledgeSyncServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ragent 真实依赖验证。默认跳过，只允许由有界运行器显式开启。
 */
@EnabledIfEnvironmentVariable(named = "RAGENT_REAL_INTEGRATION", matches = "true")
class RagentKnowledgeSyncRealIntegrationTest {
    private static final String MAPPING_KEY = "ai:knowledge:ragent:doc-mapping";
    private static final String STATUS_KEY = "ai:knowledge:ragent:sync-status";
    private static final String AUTH_TOKEN_KEY = "ai:knowledge:ragent:auth-token";

    @Test
    void shouldCompleteOnlineUpdateChunkQueryAndOfflineAgainstRealRagent() {
        LettuceConnectionFactory connectionFactory = redisConnectionFactory();
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        String runId = UUID.randomUUID().toString();
        String configKey = "validation:article:" + runId;
        String markerV1 = "RAGENT-L3-V1-" + runId;
        String markerV2 = "RAGENT-L3-V2-" + runId;
        AiKnowledgeProperties properties = ragentProperties();
        RagentKnowledgeSyncServiceImpl service = new RagentKnowledgeSyncServiceImpl(redis, properties);

        try {
            service.sync(document(configKey, "v1", markerV1));
            String firstDocId = (String) redis.opsForHash().get(MAPPING_KEY, configKey);
            assertNotNull(firstDocId);
            assertEquals("SYNCED", redis.opsForHash().get(STATUS_KEY, configKey));
            assertChunkContains(properties, redis, firstDocId, markerV1);

            service.sync(document(configKey, "v2", markerV2));
            String secondDocId = (String) redis.opsForHash().get(MAPPING_KEY, configKey);
            assertNotNull(secondDocId);
            assertNotEquals(firstDocId, secondDocId);
            assertChunkContains(properties, redis, secondDocId, markerV2);

            service.deleteStrictByConfigKey(configKey);
            assertNull(redis.opsForHash().get(MAPPING_KEY, configKey));
            assertNull(redis.opsForHash().get(STATUS_KEY, configKey));
        } finally {
            try {
                service.deleteStrictByConfigKey(configKey);
            } finally {
                redis.opsForHash().delete(MAPPING_KEY, configKey);
                redis.opsForHash().delete(STATUS_KEY, configKey);
                connectionFactory.destroy();
            }
        }
    }

    private void assertChunkContains(AiKnowledgeProperties properties, StringRedisTemplate redis,
                                     String docId, String marker) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, resolveRawToken(properties, redis));
        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = properties.getRagent().getBaseUrl().replaceAll("/+$", "");
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/knowledge-base/docs/" + docId + "/chunks?current=1&size=20",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JSONObject body = JSONObject.parseObject(response.getBody());
        assertEquals("0", body.getString("code"));
        JSONObject data = body.getJSONObject("data");
        assertNotNull(data);
        JSONArray records = data.getJSONArray("records");
        assertNotNull(records);
        assertFalse(records.isEmpty());
        assertTrue(records.stream()
                .map(JSONObject.class::cast)
                .map(value -> value.getString("content"))
                .anyMatch(content -> content != null && content.contains(marker)));
    }

    private String resolveRawToken(AiKnowledgeProperties properties, StringRedisTemplate redis) {
        String configured = properties.getRagent().getToken();
        String token = configured == null || configured.isBlank()
                ? redis.opsForValue().get(AUTH_TOKEN_KEY) : configured;
        assertNotNull(token, "Ragent 登录后应存在认证 Token");
        return token.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private AiKnowledgeDocDTO document(String configKey, String version, String marker) {
        AiKnowledgeDocDTO doc = new AiKnowledgeDocDTO();
        doc.setKey(configKey);
        doc.setCode("ragent-real-validation-" + version);
        doc.setTitle("Ragent real validation " + version);
        doc.setContent(marker);
        doc.setExportMarkdown("# Ragent real validation\n\n" + marker + "\n");
        return doc;
    }

    private AiKnowledgeProperties ragentProperties() {
        AiKnowledgeProperties properties = new AiKnowledgeProperties();
        AiKnowledgeProperties.Ragent ragent = properties.getRagent();
        ragent.setEnabled(true);
        ragent.setBaseUrl(env("RAGENT_BASE_URL", "http://127.0.0.1:9090/api/ragent"));
        ragent.setToken(System.getenv("RAGENT_TOKEN"));
        ragent.setUsername(env("RAGENT_USERNAME", "admin"));
        ragent.setPassword(env("RAGENT_PASSWORD", "admin"));
        ragent.setKbName(env("RAGENT_KB_NAME", "paicoding-community-kb"));
        ragent.setEmbeddingModel(env("RAGENT_EMBEDDING_MODEL", "qwen-emb-8b"));
        ragent.setCollectionName(env("RAGENT_COLLECTION_NAME", "paicodingcommunitykb"));
        ragent.setConnectTimeoutMs(Integer.parseInt(env("RAGENT_CONNECT_TIMEOUT_MS", "2000")));
        ragent.setReadTimeoutMs(Integer.parseInt(env("RAGENT_READ_TIMEOUT_MS", "10000")));
        ragent.setChunkWaitTimeoutMs(Integer.parseInt(env("RAGENT_CHUNK_WAIT_TIMEOUT_MS", "120000")));
        ragent.setChunkPollIntervalMs(Integer.parseInt(env("RAGENT_CHUNK_POLL_INTERVAL_MS", "1000")));
        return properties;
    }

    private LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                env("REDIS_HOST", "127.0.0.1"), Integer.parseInt(env("REDIS_PORT", "16379")));
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            configuration.setPassword(password);
        }
        return new LettuceConnectionFactory(configuration);
    }

    private String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
