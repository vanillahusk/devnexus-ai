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
import com.nageoffer.ai.ragent.infra.token.HeuristicTokenCounterService;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemorySummaryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetConversationSummaryToolTest {
    @Test
    void shouldUseExplicitActorContextAndNeverAcceptUserIdAsToolParameter() {
        ConversationMemorySummaryService summaries = mock(ConversationMemorySummaryService.class);
        when(summaries.loadLatestSummary("conv-1", "user-7")).thenReturn(ChatMessage.system("讨论了Redis可靠队列"));
        GetConversationSummaryTool tool = new GetConversationSummaryTool(summaries, new HeuristicTokenCounterService());

        AgentToolResult result = tool.execute(new GetConversationSummaryTool.Input("conv-1"),
                new AgentToolContext("user-7"));

        verify(summaries).loadLatestSummary("conv-1", "user-7");
        assertEquals("conversationSummary=讨论了Redis可靠队列", result.summary());
        assertThrows(IllegalStateException.class, () -> tool.execute(
                new GetConversationSummaryTool.Input("conv-1"), new AgentToolContext(null)));
    }
}
