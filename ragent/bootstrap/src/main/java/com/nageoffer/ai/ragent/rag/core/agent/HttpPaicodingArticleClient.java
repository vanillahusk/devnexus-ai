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
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 有大小、超时和鉴权边界的 PaiCoding 文章事实源客户端。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.agent.enabled", havingValue = "true")
public class HttpPaicodingArticleClient implements PaicodingArticleClient {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${rag.agent.paicoding.base-url:http://127.0.0.1:8080}")
    private String baseUrl;
    @Value("${rag.agent.paicoding.token-header:X-AIGC-INTERNAL-TOKEN}")
    private String tokenHeader;
    @Value("${rag.agent.paicoding.token:}")
    private String token;

    @Override
    public Optional<ArticleSnapshot> findOnlineArticle(long articleId) {
        if (articleId <= 0) throw new IllegalArgumentException("articleId必须为正数");
        if (token == null || token.isBlank()) throw new IllegalStateException("PaiCoding Agent内部令牌未配置");
        HttpUrl root = HttpUrl.parse(baseUrl);
        if (root == null || !("http".equals(root.scheme()) || "https".equals(root.scheme()))) {
            throw new IllegalStateException("PaiCoding Agent地址非法");
        }
        HttpUrl url = root.newBuilder().addPathSegments("internal/aigc/knowledge/articles")
                .addPathSegment(Long.toString(articleId)).build();
        Request request = new Request.Builder().url(url).header(tokenHeader, token).get().build();
        Call call = okHttpClient.newCall(request);
        call.timeout().timeout(10, TimeUnit.SECONDS);
        try (Response response = call.execute()) {
            if (response.code() == 404) return Optional.empty();
            if (!response.isSuccessful()) throw new IllegalStateException("PaiCoding文章事实源调用失败: HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IllegalStateException("PaiCoding文章事实源返回空响应");
            if (body.contentLength() > MAX_RESPONSE_BYTES) throw new IllegalStateException("PaiCoding文章事实源响应过大");
            byte[] bytes = body.byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IllegalStateException("PaiCoding文章事实源响应过大");
            JsonNode result = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8)).path("result");
            if (!result.isObject()) throw new IllegalStateException("PaiCoding文章事实源响应结构非法");
            return Optional.of(new ArticleSnapshot(
                    requiredPositiveLong(result, "articleId"),
                    requiredPositiveLong(result, "articleVersion"),
                    boundedText(result, "title", 300),
                    boundedText(result, "summary", 2_000),
                    boundedText(result, "content", 1_000_000),
                    nullableLong(result, "categoryId"), nullableLong(result, "updatedAt")));
        } catch (IOException failure) {
            throw new IllegalStateException("PaiCoding文章事实源不可用", failure);
        }
    }

    private long requiredPositiveLong(JsonNode node, String field) {
        long value = node.path(field).asLong(0);
        if (value <= 0) throw new IllegalStateException("PaiCoding文章事实源字段非法: " + field);
        return value;
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String boundedText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return "";
        if (!value.isTextual() || value.asText().length() > maxLength) {
            throw new IllegalStateException("PaiCoding文章事实源字段非法: " + field);
        }
        return value.asText();
    }
}
