package com.github.paicoding.forum.service.ai.service.impl;

import com.github.paicoding.forum.api.model.vo.ai.AiAgentAskReq;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RagentAiAgentServiceImplTest {
    private static final String BASE_URL = "http://ragent.test/api/ragent";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AiKnowledgeProperties properties = new AiKnowledgeProperties();
    private final RestTemplate restTemplate = new RestTemplate();
    private MockRestServiceServer server;
    private RagentAiAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        properties.getRagent().setEnabled(true);
        properties.getRagent().setBaseUrl(BASE_URL);
        properties.getRagent().setToken("Bearer service-token");
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new RagentAiAgentServiceImpl(redis, properties, restTemplate);
    }

    @Test
    void shouldUseServerCredentialAndReturnOnlyPublicAgentFields() {
        server.expect(once(), requestTo(BASE_URL + "/rag/agent/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "service-token"))
                .andExpect(jsonPath("$.question").value("Outbox 为什么可能重复投递？"))
                .andExpect(jsonPath("$.sessionId").value("session_123"))
                .andRespond(withSuccess("""
                        {
                          "code":"0",
                          "message":"success",
                          "requestId":"trace-from-ragent",
                          "data":{
                            "mode":"AGENT",
                            "answer":"Outbox 采用至少一次投递。[ref:c1]",
                            "fallback":false,
                            "failureCode":"",
                            "toolCalls":[{"toolName":"searchKnowledge","status":"SUCCESS","citationCount":1}],
                            "citations":[{
                              "chunkId":"c1",
                              "articleId":"12",
                              "articleVersion":"6",
                              "title":"可靠消息",
                              "headingPath":"Outbox",
                              "snippet":"发送成功与状态更新之间可能失败",
                              "retrievalScore":0.82,
                              "rerankScore":0.91
                            }],
                            "usage":{
                              "steps":2,
                              "toolCalls":1,
                              "retrievalCalls":1,
                              "rerankCalls":1,
                              "modelCalls":2,
                              "estimatedTokens":620,
                              "modelName":"hy3",
                              "remainingMillis":18000
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var reply = service.query(request("session_123", "Outbox 为什么可能重复投递？"), 7L);

        assertEquals("AGENT", reply.getMode());
        assertEquals("trace-from-ragent", reply.getTraceId());
        assertEquals(1, reply.getToolCalls().size());
        assertEquals("12", reply.getCitations().get(0).getArticleId());
        assertEquals(620, reply.getUsage().getEstimatedTokens());
        server.verify();
    }

    @Test
    void shouldRejectInvalidRequestBeforeCallingRagent() {
        assertThrows(RuntimeException.class, () -> service.query(request("bad session", "question"), 7L));
        assertThrows(RuntimeException.class, () -> service.query(request("session_1", " "), 7L));
        server.verify();
    }

    private AiAgentAskReq request(String sessionId, String question) {
        AiAgentAskReq request = new AiAgentAskReq();
        request.setSessionId(sessionId);
        request.setQuestion(question);
        return request;
    }
}
