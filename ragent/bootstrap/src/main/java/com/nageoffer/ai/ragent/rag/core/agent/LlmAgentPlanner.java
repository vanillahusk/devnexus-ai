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
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 使用严格 JSON 动作协议的 Planner；工具观察始终放在 USER 角色并标记为不可信。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.agent.enabled", havingValue = "true")
public class LlmAgentPlanner implements AgentPlanner {
    private static final String TEMPLATE = "prompt/agent-planner.st";
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper;
    private final TokenCounterService tokenCounterService;

    @Override
    public AgentAction next(String question, List<AgentObservation> observations, AgentExecutionBudget.State state) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(promptTemplateLoader.load(TEMPLATE)));
        messages.add(ChatMessage.user("<server_state>\n服务端剩余预算：steps=" + state.remainingSteps()
                + ", retrievalCalls=" + state.remainingRetrievalCalls()
                + ", tokens=" + state.remainingTokens() + ", millis=" + state.remainingMillis()
                + "\n已调用工具签名（不得重复）：" + state.calledToolSignatures()
                + "\n</server_state>"));
        for (AgentObservation observation : observations) {
            String citationIds = observation.citations().stream().map(item -> item.chunkId()).toList().toString();
            messages.add(ChatMessage.user("<tool_observation name=\"" + observation.toolName().value() + "\">\n"
                    + "summary=" + observation.summary() + "\nallowedCitationIds=" + citationIds + "\n"
                    + observation.untrustedContext() + "\n</tool_observation>"));
        }
        messages.add(ChatMessage.user("用户问题：" + question));
        String raw = llmService.chat(ChatRequest.builder().messages(messages).temperature(0D).topP(1D)
                .maxTokens(700).thinking(false).enableTools(false).build());
        JsonNode json = parseStrictObject(raw);
        int tokens = estimatedTokens(raw);
        String action = requiredText(json, "action").toUpperCase(Locale.ROOT);
        return switch (action) {
            case "DIRECT" -> new AgentAction.FinalAnswer(requiredText(json, "answer"), false, tokens);
            case "FINAL" -> new AgentAction.FinalAnswer(requiredText(json, "answer"), true, tokens);
            case "SEARCH" -> new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE,
                    new SearchKnowledgeTool.Input(requiredText(json, "query"), boundedTopK(json), java.util.Map.of()),
                    tokens);
            case "RELATED" -> new AgentAction.ToolCall(AgentToolName.SEARCH_RELATED_ARTICLES,
                    new SearchRelatedArticlesTool.Input(requiredText(json, "topic"), optionalText(json, "categoryId"),
                            optionalText(json, "tagIds")), tokens);
            case "DETAIL" -> new AgentAction.ToolCall(AgentToolName.GET_ARTICLE_DETAIL,
                    new GetArticleDetailTool.Input(requiredPositiveLong(json, "articleId")), tokens);
            case "SUMMARY" -> new AgentAction.ToolCall(AgentToolName.GET_CONVERSATION_SUMMARY,
                    new GetConversationSummaryTool.Input(requiredText(json, "conversationId")), tokens);
            default -> throw new IllegalArgumentException("Agent Planner返回未知动作");
        };
    }

    private JsonNode parseStrictObject(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Agent Planner返回为空");
        String cleaned = raw.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("Agent Planner未返回JSON对象");
        String prefix = cleaned.substring(0, start).strip();
        String suffix = cleaned.substring(end + 1).strip();
        if (!prefix.isEmpty() || !suffix.isEmpty()) throw new IllegalArgumentException("Agent Planner包含JSON外文本");
        try {
            JsonNode result = objectMapper.readTree(cleaned.substring(start, end + 1));
            if (!result.isObject()) throw new IllegalArgumentException("Agent Planner动作必须是JSON对象");
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Agent Planner JSON非法", failure);
        }
    }

    private String requiredText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 4_000) {
            throw new IllegalArgumentException("Agent Planner字段非法: " + field);
        }
        return value.asText();
    }

    private int boundedTopK(JsonNode json) {
        int value = json.path("topK").asInt(6);
        return Math.max(1, Math.min(8, value));
    }

    private String optionalText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) return null;
        if (value.asText().length() > 100) throw new IllegalArgumentException("Agent Planner字段过长: " + field);
        return value.asText();
    }

    private long requiredPositiveLong(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException("Agent Planner字段非法: " + field);
        }
        return value.asLong();
    }

    private int estimatedTokens(String raw) {
        Integer count = tokenCounterService.countTokens(raw);
        return count == null ? Math.max(1, raw.length() / 4) : Math.max(1, count);
    }
}
