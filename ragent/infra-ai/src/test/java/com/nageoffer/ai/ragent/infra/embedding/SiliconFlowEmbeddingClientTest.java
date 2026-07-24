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

package com.nageoffer.ai.ragent.infra.embedding;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiliconFlowEmbeddingClientTest {

    @Test
    void shouldSendQwen3ModelAndRestoreBatchOrderByResponseIndex() {
        AtomicReference<JsonObject> requestJson = new AtomicReference<>();
        OkHttpClient httpClient = clientReturning(200, """
                {"data":[
                  {"index":1,"embedding":[0.3,0.4]},
                  {"index":0,"embedding":[0.1,0.2]}
                ]}
                """, requestJson);
        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(httpClient);

        List<List<Float>> result = client.embedBatch(List.of("第一段", "第二段"), target(2));

        assertThat(result).containsExactly(List.of(0.1F, 0.2F), List.of(0.3F, 0.4F));
        assertThat(requestJson.get().get("model").getAsString()).isEqualTo("Qwen/Qwen3-Embedding-8B");
        assertThat(requestJson.get().get("dimensions").getAsInt()).isEqualTo(2);
        assertThat(requestJson.get().getAsJsonArray("input")).hasSize(2);
        assertThat(requestJson.get().get("encoding_format").getAsString()).isEqualTo("float");
    }

    @Test
    void shouldRejectVectorWhoseDimensionDoesNotMatchPgVectorContract() {
        OkHttpClient httpClient = clientReturning(200,
                "{\"data\":[{\"index\":0,\"embedding\":[0.1]}]}", new AtomicReference<>());
        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(httpClient);

        assertThatThrownBy(() -> client.embed("维度检查", target(2)))
                .isInstanceOf(ModelClientException.class)
                .satisfies(error -> assertThat(((ModelClientException) error).getErrorType())
                        .isEqualTo(ModelClientErrorType.INVALID_RESPONSE))
                .hasMessageContaining("expected=2")
                .hasMessageContaining("actual=1");
    }

    @Test
    void shouldPreserveRateLimitErrorTypeForGovernorRetryPolicy() {
        OkHttpClient httpClient = clientReturning(429, "{\"message\":\"rate limited\"}", new AtomicReference<>());
        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(httpClient);

        assertThatThrownBy(() -> client.embed("重试分类", target(2)))
                .isInstanceOf(ModelClientException.class)
                .satisfies(error -> assertThat(((ModelClientException) error).getErrorType())
                        .isEqualTo(ModelClientErrorType.RATE_LIMITED));
    }

    private OkHttpClient clientReturning(int status, String body, AtomicReference<JsonObject> requestJson) {
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

    private ModelTarget target(int dimension) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl("http://localhost");
        provider.setApiKey("unit-test-key");
        provider.setEndpoints(new HashMap<>(java.util.Map.of("embedding", "/v1/embeddings")));

        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("qwen-emb-8b");
        candidate.setProvider("siliconflow");
        candidate.setModel("Qwen/Qwen3-Embedding-8B");
        candidate.setDimension(dimension);
        return new ModelTarget(candidate.getId(), candidate, provider);
    }
}
