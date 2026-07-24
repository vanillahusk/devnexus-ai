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

import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalService;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrieveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.apache.skywalking.apm.toolkit.trace.Trace;

import java.util.Locale;
import java.util.Map;

/** 按主题返回去重后的在线相关文章引用，不返回无限正文列表。 */
@Component
@RequiredArgsConstructor
public class SearchRelatedArticlesTool implements AgentTool<SearchRelatedArticlesTool.Input> {
    private final TrustedRetrievalService trustedRetrievalService;

    @Override public AgentToolName name() { return AgentToolName.SEARCH_RELATED_ARTICLES; }
    @Override public Class<Input> inputType() { return Input.class; }

    @Override
    public String normalizedSignature(Input input) {
        validate(input);
        return input.topic().strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")
                + ";categoryId=" + value(input.categoryId()) + ";tagIds=" + value(input.tagIds());
    }

    @Override
    @Trace(operationName = "rag.agent.tool.search_related_articles")
    public AgentToolResult execute(Input input) {
        validate(input);
        Map<String, Object> filters = new java.util.LinkedHashMap<>();
        if (input.categoryId() != null && !input.categoryId().isBlank()) filters.put("categoryId", input.categoryId());
        if (input.tagIds() != null && !input.tagIds().isBlank()) filters.put("tagIds", input.tagIds());
        TrustedRetrievalResult result = trustedRetrievalService.retrieve(TrustedRetrieveRequest.builder()
                .query(input.topic()).candidateTopK(20).topK(8).maxContextTokens(4000)
                .metadataFilters(filters).build());
        long articles = result.citations().stream().map(TrustedRetrievalResult.Citation::articleId).distinct().count();
        return new AgentToolResult("relatedArticles=" + articles + ",decision=" + result.decisionCode(),
                Math.max(1, result.contextTokens()), result.citations(), result);
    }

    private void validate(Input input) {
        if (input == null || input.topic() == null || input.topic().isBlank() || input.topic().length() > 300) {
            throw new IllegalArgumentException("searchRelatedArticles.topic长度必须在1到300之间");
        }
        if (tooLong(input.categoryId()) || tooLong(input.tagIds())) {
            throw new IllegalArgumentException("searchRelatedArticles过滤参数过长");
        }
    }

    private boolean tooLong(String value) { return value != null && value.length() > 100; }
    private String value(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT); }

    public record Input(String topic, String categoryId, String tagIds) {}
}
