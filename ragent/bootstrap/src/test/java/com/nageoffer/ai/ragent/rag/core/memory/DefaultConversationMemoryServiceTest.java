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

package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultConversationMemoryServiceTest {

    @Test
    void shouldCombineSummaryAndRecentWindowOnDedicatedExecutor() {
        ConversationMemoryStore store = mock(ConversationMemoryStore.class);
        ConversationMemorySummaryService summaries = mock(ConversationMemorySummaryService.class);
        RecordingExecutor executor = new RecordingExecutor();
        DefaultConversationMemoryService service = new DefaultConversationMemoryService(store, summaries, executor);
        ChatMessage summary = ChatMessage.system("known facts");
        when(summaries.loadLatestSummary("c1", "u1")).thenReturn(summary);
        when(summaries.decorateIfNeeded(summary)).thenReturn(ChatMessage.system("对话摘要：known facts"));
        when(store.loadHistory("c1", "u1")).thenReturn(List.of(
                ChatMessage.user("recent question"), ChatMessage.assistant("recent answer")));

        List<ChatMessage> result = service.load("c1", "u1");

        assertEquals(2, executor.executions);
        assertEquals(3, result.size());
        assertEquals("对话摘要：known facts", result.get(0).getContent());
        assertEquals("recent question", result.get(1).getContent());
    }

    @Test
    void shouldPersistMessageBeforeSchedulingIncrementalSummary() {
        ConversationMemoryStore store = mock(ConversationMemoryStore.class);
        ConversationMemorySummaryService summaries = mock(ConversationMemorySummaryService.class);
        DefaultConversationMemoryService service = new DefaultConversationMemoryService(store, summaries, Runnable::run);
        ChatMessage answer = ChatMessage.assistant("answer");
        when(store.append("c1", "u1", answer)).thenReturn("m9");

        assertEquals("m9", service.append("c1", "u1", answer));
        verify(store).append("c1", "u1", answer);
        verify(summaries).compressIfNeeded("c1", "u1", answer);
    }

    private static final class RecordingExecutor implements Executor {
        private int executions;

        @Override
        public void execute(Runnable command) {
            executions++;
            command.run();
        }
    }
}
