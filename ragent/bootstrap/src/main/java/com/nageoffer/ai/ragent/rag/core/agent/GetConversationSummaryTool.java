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

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemorySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.apache.skywalking.apm.toolkit.trace.Trace;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 只能读取当前登录用户自己的有限会话摘要，不能由工具参数指定 userId。 */
@Component
@RequiredArgsConstructor
public class GetConversationSummaryTool implements AgentTool<GetConversationSummaryTool.Input> {
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private final ConversationMemorySummaryService summaryService;
    private final TokenCounterService tokenCounterService;

    @Override public AgentToolName name() { return AgentToolName.GET_CONVERSATION_SUMMARY; }
    @Override public Class<Input> inputType() { return Input.class; }

    @Override
    public String normalizedSignature(Input input) {
        validate(input);
        return input.conversationId().toLowerCase(Locale.ROOT);
    }

    @Override
    @Trace(operationName = "rag.agent.tool.get_conversation_summary")
    public AgentToolResult execute(Input input) {
        return execute(input, new AgentToolContext(null));
    }

    @Override
    public AgentToolResult execute(Input input, AgentToolContext context) {
        validate(input);
        String userId = context == null ? null : context.userId();
        if (userId == null || userId.isBlank()) throw new IllegalStateException("Agent会话摘要要求登录身份");
        ChatMessage summary = summaryService.loadLatestSummary(input.conversationId(), userId);
        String content = summary == null || summary.getContent() == null ? "" : summary.getContent().strip();
        if (content.length() > 500) content = content.substring(0, 500);
        Integer tokens = tokenCounterService.countTokens(content);
        return new AgentToolResult("conversationSummary=" + content, tokens == null ? 0 : tokens,
                List.of(), null);
    }

    private void validate(Input input) {
        if (input == null || input.conversationId() == null
                || !SAFE_ID.matcher(input.conversationId()).matches()) {
            throw new IllegalArgumentException("getConversationSummary.conversationId非法");
        }
    }

    public record Input(String conversationId) {}
}
