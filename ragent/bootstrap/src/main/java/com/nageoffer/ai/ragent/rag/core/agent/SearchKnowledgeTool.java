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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 只读知识检索工具；可见性由 TrustedRetrievalService 再次强制。 */
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AgentTool<SearchKnowledgeTool.Input> {
    private static final Set<String> ALLOWED_FILTERS = Set.of("articleId", "articleVersion", "categoryId", "tagIds");
    private final TrustedRetrievalService trustedRetrievalService;

    @Override public AgentToolName name() { return AgentToolName.SEARCH_KNOWLEDGE; }
    @Override public Class<Input> inputType() { return Input.class; }

    @Override
    public String normalizedSignature(Input input) {
        validate(input);
        String query = input.query().strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return "query=" + query + ";topK=" + input.topK() + ";filters=" + normalizedFilters(input.filters());
    }

    @Override
    @Trace(operationName = "rag.agent.tool.search_knowledge")
    public AgentToolResult execute(Input input) {
        validate(input);
        TrustedRetrievalResult result = trustedRetrievalService.retrieve(TrustedRetrieveRequest.builder()
                .query(input.query()).candidateTopK(20).topK(input.topK()).maxContextTokens(4000)
                .metadataFilters(input.filters()).build());
        String summary = "answerable=" + result.answerable() + ",decision=" + result.decisionCode()
                + ",citations=" + result.citations().size();
        return new AgentToolResult(summary, Math.max(1, result.contextTokens()), result.citations(), result);
    }

    private void validate(Input input) {
        if (input == null || input.query() == null || input.query().isBlank() || input.query().length() > 500) {
            throw new IllegalArgumentException("searchKnowledge.query长度必须在1到500之间");
        }
        if (input.topK() < 1 || input.topK() > 8) throw new IllegalArgumentException("searchKnowledge.topK必须在1到8之间");
        if (input.filters() != null) {
            for (Map.Entry<String, Object> entry : input.filters().entrySet()) {
                if (!ALLOWED_FILTERS.contains(entry.getKey()) || entry.getValue() == null
                        || entry.getValue().toString().length() > 100) {
                    throw new IllegalArgumentException("searchKnowledge过滤参数越权或非法: " + entry.getKey());
                }
            }
        }
    }

    private String normalizedFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return "{}";
        Map<String, String> sorted = new java.util.TreeMap<>();
        filters.forEach((key, value) -> sorted.put(key, value.toString().strip().toLowerCase(Locale.ROOT)));
        return new LinkedHashMap<>(sorted).toString();
    }

    public record Input(String query, int topK, Map<String, Object> filters) {
        public Input { filters = filters == null ? Map.of() : Map.copyOf(filters); }
    }
}
