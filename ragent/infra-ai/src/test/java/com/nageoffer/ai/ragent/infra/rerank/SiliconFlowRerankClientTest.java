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

package com.nageoffer.ai.ragent.infra.rerank;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.http.ModelClientErrorType;
import com.nageoffer.ai.ragent.infra.http.ModelClientException;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiliconFlowRerankClientTest {

    @Test
    void shouldSendQwen3RequestAndRestoreChunksByResultIndex() {
        AtomicReference<JsonObject> requestJson = new AtomicReference<>();
        OkHttpClient httpClient = clientReturning(200, """
                {"id":"rerank-1","results":[
                  {"index":1,"relevance_score":0.93},
                  {"index":0,"relevance_score":0.12}
                ]}
                """, requestJson);
        SiliconFlowRerankClient client = new SiliconFlowRerankClient(httpClient);

        List<RetrievedChunk> result = client.rerank("Outbox", List.of(
                chunk("c1", "Redis cache"),
                chunk("c2", "Outbox and RocketMQ")), 2, target());

        assertThat(result).extracting(RetrievedChunk::getId).containsExactly("c2", "c1");
        assertThat(result).extracting(RetrievedChunk::getScore).containsExactly(0.93F, 0.12F);
        assertThat(requestJson.get().get("model").getAsString()).isEqualTo("Qwen/Qwen3-Reranker-8B");
        assertThat(requestJson.get().getAsJsonArray("documents")).hasSize(2);
        assertThat(requestJson.get().get("top_n").getAsInt()).isEqualTo(2);
        assertThat(requestJson.get().get("return_documents").getAsBoolean()).isFalse();
    }

    @Test
    void shouldDeduplicateCandidatesBeforeCallingProvider() {
        AtomicReference<JsonObject> requestJson = new AtomicReference<>();
        OkHttpClient httpClient = clientReturning(200,
                "{\"results\":[{\"index\":0,\"relevance_score\":0.8}]}",
                requestJson);
        SiliconFlowRerankClient client = new SiliconFlowRerankClient(httpClient);

        List<RetrievedChunk> result = client.rerank("query",
                List.of(chunk("c1", "first"), chunk("c1", "duplicate")), 1, target());

        assertThat(result).hasSize(1);
        assertThat(requestJson.get().getAsJsonArray("documents")).hasSize(1);
    }

    @Test
    void shouldPreserveRateLimitAndRejectInvalidIndexes() {
        SiliconFlowRerankClient rateLimited = new SiliconFlowRerankClient(
                clientReturning(429, "{\"message\":\"rate limited\"}", new AtomicReference<>()));
        assertThatThrownBy(() -> rateLimited.rerank(
                "query", List.of(chunk("c1", "text")), 1, target()))
                .isInstanceOf(ModelClientException.class)
                .satisfies(error -> assertThat(((ModelClientException) error).getErrorType())
                        .isEqualTo(ModelClientErrorType.RATE_LIMITED));

        SiliconFlowRerankClient invalid = new SiliconFlowRerankClient(
                clientReturning(200,
                        "{\"results\":[{\"index\":2,\"relevance_score\":0.8}]}",
                        new AtomicReference<>()));
        assertThatThrownBy(() -> invalid.rerank(
                "query", List.of(chunk("c1", "text")), 1, target()))
                .isInstanceOf(ModelClientException.class)
                .hasMessageContaining("index");
    }

    private RetrievedChunk chunk(String id, String text) {
        return new RetrievedChunk(id, text, 0F, Map.of("articleId", id));
    }

    private ModelTarget target() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl("http://localhost");
        provider.setApiKey("unit-test-key");
        provider.setEndpoints(new HashMap<>(Map.of("rerank", "/v1/rerank")));
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("qwen3-rerank-siliconflow");
        candidate.setProvider("siliconflow");
        candidate.setModel("Qwen/Qwen3-Reranker-8B");
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private OkHttpClient clientReturning(
            int status,
            String body,
            AtomicReference<JsonObject> requestJson) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    if (chain.request().body() != null) {
                        okio.Buffer buffer = new okio.Buffer();
                        chain.request().body().writeTo(buffer);
                        requestJson.set(JsonParser.parseString(buffer.readUtf8()).getAsJsonObject());
                    }
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(status)
                            .message(status == 200 ? "OK" : "ERROR")
                            .body(ResponseBody.create(body, MediaType.get("application/json")))
                            .build();
                })
                .build();
    }
}
