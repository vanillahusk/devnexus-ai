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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SiliconFlow OpenAI 风格 Rerank 适配器。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SiliconFlowRerankClient implements RerankClient {
    private static final String DEFAULT_INSTRUCTION =
            "Given a web search query, retrieve relevant passages that answer the query.";

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    public List<RetrievedChunk> rerank(
            String query,
            List<RetrievedChunk> candidates,
            int topN,
            ModelTarget target) {
        List<RetrievedChunk> deduplicated = deduplicate(candidates);
        if (query == null || query.isBlank() || deduplicated.isEmpty() || topN <= 0) {
            return List.of();
        }
        int requestedTopN = Math.min(topN, deduplicated.size());
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", requireModel(target));
        requestBody.addProperty("query", query);
        requestBody.addProperty("instruction", DEFAULT_INSTRUCTION);
        requestBody.addProperty("top_n", requestedTopN);
        requestBody.addProperty("return_documents", false);
        JsonArray documents = new JsonArray();
        deduplicated.forEach(chunk -> documents.add(chunk.getText() == null ? "" : chunk.getText()));
        requestBody.add("documents", documents);

        AIModelProperties.ProviderConfig provider = requireProvider(target);
        Request request = new Request.Builder()
                .url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.RERANK))
                .post(RequestBody.create(gson.toJson(requestBody), HttpMediaTypes.JSON))
                .addHeader("Content-Type", HttpMediaTypes.JSON_UTF8_HEADER)
                .addHeader("Authorization", "Bearer " + provider.getApiKey())
                .build();

        JsonObject responseJson;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("SiliconFlow rerank HTTP error: status={}", response.code());
                throw new ModelClientException(
                        "调用 SiliconFlow Rerank 失败: HTTP " + response.code(),
                        classifyStatus(response.code()),
                        response.code());
            }
            responseJson = parseJsonBody(response.body());
        } catch (IOException exception) {
            throw new ModelClientException(
                    "调用 SiliconFlow Rerank 失败: " + exception.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR,
                    null,
                    exception);
        }
        return parseResults(responseJson, deduplicated, requestedTopN);
    }

    private List<RetrievedChunk> deduplicate(List<RetrievedChunk> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<RetrievedChunk> result = new ArrayList<>(candidates.size());
        Set<String> seen = new HashSet<>();
        for (RetrievedChunk candidate : candidates) {
            if (candidate != null && candidate.getId() != null && seen.add(candidate.getId())) {
                result.add(candidate);
            }
        }
        return result;
    }

    private List<RetrievedChunk> parseResults(
            JsonObject root,
            List<RetrievedChunk> candidates,
            int requestedTopN) {
        JsonArray results = root == null ? null : root.getAsJsonArray("results");
        if (results == null || results.isEmpty()) {
            throw new ModelClientException(
                    "SiliconFlow Rerank 响应中缺少 results",
                    ModelClientErrorType.INVALID_RESPONSE,
                    null);
        }
        List<RetrievedChunk> reranked = new ArrayList<>(requestedTopN);
        Set<Integer> seenIndexes = new HashSet<>();
        for (JsonElement element : results) {
            JsonObject item = element.getAsJsonObject();
            if (!item.has("index") || !item.has("relevance_score")) {
                throw new ModelClientException(
                        "SiliconFlow Rerank 结果缺少 index 或 relevance_score",
                        ModelClientErrorType.INVALID_RESPONSE,
                        null);
            }
            int index = item.get("index").getAsInt();
            if (index < 0 || index >= candidates.size() || !seenIndexes.add(index)) {
                throw new ModelClientException(
                        "SiliconFlow Rerank 结果 index 非法或重复: " + index,
                        ModelClientErrorType.INVALID_RESPONSE,
                        null);
            }
            RetrievedChunk source = candidates.get(index);
            reranked.add(RetrievedChunk.builder()
                    .id(source.getId())
                    .text(source.getText())
                    .metadata(source.getMetadata())
                    .score(item.get("relevance_score").getAsFloat())
                    .build());
            if (reranked.size() >= requestedTopN) break;
        }
        if (reranked.size() != requestedTopN) {
            throw new ModelClientException(
                    "SiliconFlow Rerank 返回数量不匹配: expected=" + requestedTopN
                            + ", actual=" + reranked.size(),
                    ModelClientErrorType.INVALID_RESPONSE,
                    null);
        }
        return reranked;
    }

    private AIModelProperties.ProviderConfig requireProvider(ModelTarget target) {
        if (target == null || target.provider() == null) {
            throw new IllegalStateException("SiliconFlow rerank provider config is missing");
        }
        return target.provider();
    }

    private String requireModel(ModelTarget target) {
        if (target == null || target.candidate() == null
                || target.candidate().getModel() == null
                || target.candidate().getModel().isBlank()) {
            throw new IllegalStateException("SiliconFlow rerank model name is missing");
        }
        return target.candidate().getModel();
    }

    private JsonObject parseJsonBody(ResponseBody body) throws IOException {
        if (body == null) {
            throw new ModelClientException(
                    "SiliconFlow Rerank 响应为空",
                    ModelClientErrorType.INVALID_RESPONSE,
                    null);
        }
        return JsonParser.parseString(body.string()).getAsJsonObject();
    }

    private ModelClientErrorType classifyStatus(int status) {
        if (status == 401 || status == 403) return ModelClientErrorType.UNAUTHORIZED;
        if (status == 429) return ModelClientErrorType.RATE_LIMITED;
        if (status >= 500) return ModelClientErrorType.SERVER_ERROR;
        return ModelClientErrorType.CLIENT_ERROR;
    }
}
