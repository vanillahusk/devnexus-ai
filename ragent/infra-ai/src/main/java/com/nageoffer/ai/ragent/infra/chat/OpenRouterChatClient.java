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

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import com.nageoffer.ai.ragent.infra.http.HttpMediaTypes;
import com.nageoffer.ai.ragent.infra.http.ModelClientErrorType;
import com.nageoffer.ai.ragent.infra.http.ModelClientException;
import com.nageoffer.ai.ragent.infra.http.ModelUrlResolver;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterChatClient implements ChatClient {

    private final OkHttpClient httpClient;
    @Qualifier("modelStreamExecutor")
    private final Executor modelStreamExecutor;
    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return ModelProvider.OPEN_ROUTER.getId();
    }

    @Override
    @RagTraceNode(name = "openrouter-chat", type = "LLM_PROVIDER")
    public String chat(ChatRequest request, ModelTarget target) {
        Request httpRequest = buildRequest(request, target, false);
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw httpFailure("同步", response.code());
            }
            return extractContent(parseBody(response.body()));
        } catch (IOException failure) {
            throw new ModelClientException("OpenRouter 同步请求失败: " + failure.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR, null, failure);
        }
    }

    @Override
    @RagTraceNode(name = "openrouter-stream-chat", type = "LLM_PROVIDER")
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        Call call = httpClient.newCall(buildRequest(request, target, true));
        return StreamAsyncExecutor.submit(
                modelStreamExecutor,
                call,
                callback,
                cancelled -> consumeStream(call, callback, cancelled)
        );
    }

    private void consumeStream(Call call, StreamCallback callback, AtomicBoolean cancelled) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw httpFailure("流式", response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw invalidResponse("OpenRouter 流式响应为空");
            }
            BufferedSource source = body.source();
            boolean completed = false;
            while (!cancelled.get()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                if (line.isBlank() || line.startsWith(":")) {
                    continue;
                }
                OpenAIStyleSseParser.ParsedEvent event = OpenAIStyleSseParser.parseLine(line, gson, false);
                if (event.hasContent()) {
                    callback.onContent(event.content());
                }
                if (event.completed()) {
                    callback.onComplete();
                    completed = true;
                    break;
                }
            }
            if (!cancelled.get() && !completed) {
                throw invalidResponse("OpenRouter 流式响应异常结束");
            }
        } catch (Exception failure) {
            callback.onError(failure);
        }
    }

    private Request buildRequest(ChatRequest request, ModelTarget target, boolean stream) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);
        JsonObject body = new JsonObject();
        body.addProperty("model", requireModel(target));
        body.addProperty("stream", stream);
        body.add("messages", messages(request));
        if (request.getTemperature() != null) {
            body.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.addProperty("top_p", request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            body.addProperty("max_tokens", request.getMaxTokens());
        }
        if (Boolean.TRUE.equals(request.getThinking())) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "low");
            reasoning.addProperty("exclude", true);
            body.add("reasoning", reasoning);
        }
        return new Request.Builder()
                .url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.CHAT))
                .post(RequestBody.create(body.toString(), HttpMediaTypes.JSON))
                .addHeader("Content-Type", HttpMediaTypes.JSON_UTF8_HEADER)
                .addHeader("Authorization", "Bearer " + provider.getApiKey())
                .build();
    }

    private JsonArray messages(ChatRequest request) {
        JsonArray result = new JsonArray();
        List<ChatMessage> messages = request == null ? null : request.getMessages();
        if (CollUtil.isEmpty(messages)) {
            throw new IllegalArgumentException("OpenRouter messages 不能为空");
        }
        for (ChatMessage message : messages) {
            if (message == null || message.getRole() == null || message.getContent() == null) {
                throw new IllegalArgumentException("OpenRouter message 不完整");
            }
            JsonObject item = new JsonObject();
            item.addProperty("role", message.getRole().name().toLowerCase());
            item.addProperty("content", message.getContent());
            result.add(item);
        }
        return result;
    }

    private AIModelProperties.ProviderConfig requireProvider(ModelTarget target) {
        if (target == null || target.provider() == null) {
            throw new IllegalStateException("OpenRouter 提供商配置缺失");
        }
        if (target.provider().getApiKey() == null || target.provider().getApiKey().isBlank()) {
            throw new IllegalStateException("OpenRouter API密钥缺失");
        }
        return target.provider();
    }

    private String requireModel(ModelTarget target) {
        if (target.candidate() == null || target.candidate().getModel() == null
                || target.candidate().getModel().isBlank()) {
            throw new IllegalStateException("OpenRouter 模型名称缺失");
        }
        return target.candidate().getModel();
    }

    private JsonObject parseBody(ResponseBody body) throws IOException {
        if (body == null) {
            throw invalidResponse("OpenRouter 响应为空");
        }
        return gson.fromJson(body.string(), JsonObject.class);
    }

    private String extractContent(JsonObject root) {
        if (root == null || !root.has("choices") || !root.get("choices").isJsonArray()) {
            throw invalidResponse("OpenRouter 响应缺少 choices");
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty() || !choices.get(0).isJsonObject()) {
            throw invalidResponse("OpenRouter choices 为空");
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("message") || !choice.get("message").isJsonObject()) {
            throw invalidResponse("OpenRouter 响应缺少 message");
        }
        JsonObject message = choice.getAsJsonObject("message");
        if (!message.has("content") || message.get("content").isJsonNull()) {
            throw invalidResponse("OpenRouter 响应缺少 content");
        }
        String content = message.get("content").getAsString();
        if (content.isBlank()) {
            throw invalidResponse("OpenRouter content 为空");
        }
        return content;
    }

    private ModelClientException httpFailure(String mode, int status) {
        log.warn("OpenRouter {}请求失败: status={}", mode, status);
        return new ModelClientException("OpenRouter " + mode + "请求失败: HTTP " + status,
                classify(status), status);
    }

    private ModelClientException invalidResponse(String message) {
        return new ModelClientException(message, ModelClientErrorType.INVALID_RESPONSE, null);
    }

    private ModelClientErrorType classify(int status) {
        if (status == 401 || status == 403) {
            return ModelClientErrorType.UNAUTHORIZED;
        }
        if (status == 429) {
            return ModelClientErrorType.RATE_LIMITED;
        }
        if (status >= 500) {
            return ModelClientErrorType.SERVER_ERROR;
        }
        return ModelClientErrorType.CLIENT_ERROR;
    }
}
