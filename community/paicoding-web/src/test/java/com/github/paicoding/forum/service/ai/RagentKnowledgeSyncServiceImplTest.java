package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.impl.RagentKnowledgeSyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RagentKnowledgeSyncServiceImplTest {
    private static final String BASE_URL = "http://ragent.test/api/ragent";
    private static final String MAPPING_KEY = "ai:knowledge:ragent:doc-mapping";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("rawtypes")
    private final ValueOperations valueOperations = mock(ValueOperations.class);
    @SuppressWarnings("rawtypes")
    private final HashOperations hashOperations = mock(HashOperations.class);
    private final AiKnowledgeProperties properties = new AiKnowledgeProperties();
    private final RestTemplate restTemplate = new RestTemplate();
    private MockRestServiceServer server;
    private RagentKnowledgeSyncServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties.getRagent().setEnabled(true);
        properties.getRagent().setBaseUrl(BASE_URL);
        properties.getRagent().setToken("test-token");
        properties.getRagent().setChunkWaitTimeoutMs(1000);
        properties.getRagent().setChunkPollIntervalMs(100);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("ai:knowledge:ragent:kb-id")).thenReturn("kb-1");
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new RagentKnowledgeSyncServiceImpl(redis, properties, restTemplate);
    }

    @Test
    void shouldMatchAssociatedRagentDefaultHttpContract() {
        AiKnowledgeProperties defaults = new AiKnowledgeProperties();

        assertEquals("http://localhost:9090/api/ragent", defaults.getRagent().getBaseUrl());
        assertEquals("qwen-emb-8b", defaults.getRagent().getEmbeddingModel());
        assertEquals("paicodingcommunitykb", defaults.getRagent().getCollectionName());
        assertEquals(500, defaults.getRagent().getConnectTimeoutMs());
        assertEquals(2500, defaults.getRagent().getReadTimeoutMs());
    }

    @Test
    void shouldRejectCollectionNameThatCannotBeUsedAsS3Bucket() {
        properties.getRagent().setCollectionName("paicoding_community_kb");

        assertThrows(RuntimeException.class, () -> service.sync(document()));
        server.verify();
    }

    @Test
    void shouldSendRawSaTokenAuthorizationValue() {
        properties.getRagent().setToken("Bearer configured-token");
        when(hashOperations.get(MAPPING_KEY, "article:12")).thenReturn(null);
        expectUpload("doc-new", "configured-token");
        expectJson(HttpMethod.POST, "/knowledge-base/docs/doc-new/chunk", success(null), "configured-token");
        expectJson(HttpMethod.GET, "/knowledge-base/docs/doc-new",
                success("{\"id\":\"doc-new\",\"status\":\"success\",\"chunkCount\":2}"),
                "configured-token");

        service.sync(document());

        server.verify();
    }

    @Test
    void shouldWaitUntilNewDocumentIsSearchableBeforeDeletingOldVersion() {
        when(hashOperations.get(MAPPING_KEY, "article:12")).thenReturn("doc-old");
        expectUpload("doc-new");
        expectJson(HttpMethod.POST, "/knowledge-base/docs/doc-new/chunk", success(null));
        expectJson(HttpMethod.GET, "/knowledge-base/docs/doc-new",
                success("{\"id\":\"doc-new\",\"status\":\"success\",\"chunkCount\":2}"));
        expectJson(HttpMethod.DELETE, "/knowledge-base/docs/doc-old", success(null));

        service.sync(document());

        server.verify();
        verify(hashOperations).put(MAPPING_KEY, "article:12", "doc-new");
    }

    @Test
    void shouldKeepOldMappingAndCleanNewDocumentWhenChunkingFails() {
        when(hashOperations.get(MAPPING_KEY, "article:12")).thenReturn("doc-old");
        expectUpload("doc-new");
        expectJson(HttpMethod.POST, "/knowledge-base/docs/doc-new/chunk", success(null));
        expectJson(HttpMethod.GET, "/knowledge-base/docs/doc-new",
                success("{\"id\":\"doc-new\",\"status\":\"failed\",\"chunkCount\":0}"));
        expectJson(HttpMethod.DELETE, "/knowledge-base/docs/doc-new", success(null));

        assertThrows(RuntimeException.class, () -> service.sync(document()));

        server.verify();
        verify(hashOperations, never()).put(MAPPING_KEY, "article:12", "doc-new");
    }

    @Test
    void shouldCallGenerationControlPlaneWithWatermarkAndParseState() {
        server.expect(once(), requestTo(BASE_URL + "/admin/rag/index-generations/begin"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "test-token"))
                .andExpect(jsonPath("$.collectionName").value("paicodingcommunitykb"))
                .andExpect(jsonPath("$.generationLabel").value("g2"))
                .andExpect(jsonPath("$.startWatermark").value(10))
                .andRespond(withSuccess(success("{\"logicalCollection\":\"paicodingcommunitykb\","
                        + "\"activeGeneration\":\"paicodingcommunitykb\","
                        + "\"buildingGeneration\":\"paicodingcommunitykb--g2\","
                        + "\"status\":\"BUILDING\",\"startWatermark\":10,"
                        + "\"appliedWatermark\":10,\"targetWatermark\":10,\"reconciled\":false}"),
                        MediaType.APPLICATION_JSON));

        var state = service.beginGeneration("g2", 10L);

        assertEquals("paicodingcommunitykb--g2", state.buildingGeneration());
        assertEquals(10, state.startWatermark());
        server.verify();
    }

    @Test
    void shouldParseGenerationArticleVersionSummary() {
        expectJson(HttpMethod.GET,
                "/admin/rag/index-generations/article-versions?collectionName=paicodingcommunitykb--g2",
                success("{\"12\":{\"minVersion\":6,\"maxVersion\":6,\"chunkCount\":3}}"));

        var versions = service.generationArticleVersions("paicodingcommunitykb--g2");

        assertEquals(6, versions.get(12L).minVersion());
        assertEquals(3, versions.get(12L).chunkCount());
        server.verify();
    }

    private void expectUpload(String docId) {
        expectUpload(docId, "test-token");
    }

    private void expectUpload(String docId, String token) {
        server.expect(once(), requestTo(BASE_URL + "/knowledge-base/kb-1/docs/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", token))
                .andRespond(withSuccess(success("{\"id\":\"" + docId + "\"}"), MediaType.APPLICATION_JSON));
    }

    private void expectJson(HttpMethod httpMethod, String path, String body) {
        expectJson(httpMethod, path, body, "test-token");
    }

    private void expectJson(HttpMethod httpMethod, String path, String body, String token) {
        server.expect(once(), requestTo(BASE_URL + path))
                .andExpect(method(httpMethod))
                .andExpect(header("Authorization", token))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String success(String data) {
        return "{\"code\":\"0\",\"message\":\"success\",\"data\":"
                + (data == null ? "null" : data) + "}";
    }

    private AiKnowledgeDocDTO document() {
        AiKnowledgeDocDTO doc = new AiKnowledgeDocDTO();
        doc.setKey("article:12");
        doc.setCode("article-12-v6");
        doc.setTitle("可靠知识索引");
        doc.setExportMarkdown("# 可靠知识索引\n\n正文");
        return doc;
    }
}
