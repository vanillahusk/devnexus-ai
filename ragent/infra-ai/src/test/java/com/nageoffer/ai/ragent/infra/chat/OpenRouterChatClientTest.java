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

package com.nageoffer.ai.ragent.infra.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
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

class OpenRouterChatClientTest {

    @Test
    void shouldCallHy3FreeWithOpenAiCompatibleMessagesAndBoundedReasoning() {
        AtomicReference<JsonObject> captured = new AtomicReference<>();
        OpenRouterChatClient client = new OpenRouterChatClient(http(200,
                "{\"choices\":[{\"message\":{\"content\":\"基于证据回答 [ref:c1]\"}}]}", captured), Runnable::run);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.system("只使用证据"), ChatMessage.user("如何保证可靠投递？")))
                .temperature(0.2D)
                .maxTokens(256)
                .thinking(true)
                .build();

        String answer = client.chat(request, target());

        assertThat(answer).isEqualTo("基于证据回答 [ref:c1]");
        assertThat(captured.get().get("model").getAsString()).isEqualTo("tencent/hy3:free");
        assertThat(captured.get().getAsJsonArray("messages")).hasSize(2);
        assertThat(captured.get().get("max_tokens").getAsInt()).isEqualTo(256);
        assertThat(captured.get().getAsJsonObject("reasoning").get("effort").getAsString()).isEqualTo("low");
        assertThat(captured.get().getAsJsonObject("reasoning").get("exclude").getAsBoolean()).isTrue();
    }

    @Test
    void shouldClassifyFreeModelRateLimitWithoutLeakingResponseBody() {
        OpenRouterChatClient client = new OpenRouterChatClient(http(429,
                "{\"error\":{\"message\":\"provider details\"}}", new AtomicReference<>()), Runnable::run);

        assertThatThrownBy(() -> client.chat(ChatRequest.builder()
                        .messages(List.of(ChatMessage.user("test"))).build(), target()))
                .isInstanceOf(ModelClientException.class)
                .satisfies(error -> assertThat(((ModelClientException) error).getErrorType())
                        .isEqualTo(ModelClientErrorType.RATE_LIMITED))
                .hasMessageNotContaining("provider details");
    }

    private OkHttpClient http(int status, String responseBody, AtomicReference<JsonObject> captured) {
        return new OkHttpClient.Builder().addInterceptor(chain -> {
            if (chain.request().body() != null) {
                okio.Buffer buffer = new okio.Buffer();
                chain.request().body().writeTo(buffer);
                captured.set(JsonParser.parseString(buffer.readUtf8()).getAsJsonObject());
            }
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message(status == 200 ? "OK" : "ERROR")
                    .body(ResponseBody.create(responseBody, MediaType.get("application/json")))
                    .build();
        }).build();
    }

    private ModelTarget target() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl("http://localhost");
        provider.setApiKey("unit-test-key");
        provider.setEndpoints(new HashMap<>(Map.of("chat", "/api/v1/chat/completions")));
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("hy3-free");
        candidate.setProvider("openrouter");
        candidate.setModel("tencent/hy3:free");
        return new ModelTarget(candidate.getId(), candidate, provider);
    }
}
