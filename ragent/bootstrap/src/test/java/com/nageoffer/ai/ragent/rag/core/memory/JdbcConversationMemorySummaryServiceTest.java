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
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.service.ConversationGroupService;
import com.nageoffer.ai.ragent.rag.service.ConversationMessageService;
import com.nageoffer.ai.ragent.rag.service.bo.ConversationSummaryBO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcConversationMemorySummaryServiceTest {

    @Test
    void shouldSummarizeOnlyMessagesOlderThanRecentWindowAndPersistWatermark() throws Exception {
        ConversationGroupService groups = mock(ConversationGroupService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        LLMService llm = mock(LLMService.class);
        PromptTemplateLoader prompts = mock(PromptTemplateLoader.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        MemoryProperties properties = new MemoryProperties();
        properties.setSummaryEnabled(true);
        properties.setSummaryStartTurns(5);
        properties.setHistoryKeepTurns(4);
        properties.setSummaryMaxChars(200);
        when(redisson.getLock("ragent:memory:summary:lock:u1:c1")).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.MINUTES.toMillis(5), TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(groups.countUserMessages("c1", "u1")).thenReturn(6L);
        when(groups.listLatestUserOnlyMessages("c1", "u1", 4)).thenReturn(List.of(
                message("10", "user", "newest"), message("8", "user", "newer"),
                message("6", "user", "recent"), message("4", "user", "window-start")));
        when(groups.listMessagesBetweenIds("c1", "u1", null, "4")).thenReturn(List.of(
                message("1", "user", "old question"), message("2", "assistant", "old answer"),
                message("3", "user", "next old question")));
        when(prompts.render(any(), anyMap())).thenReturn("summarize safely");
        when(llm.chat(any(com.nageoffer.ai.ragent.framework.convention.ChatRequest.class)))
                .thenReturn("durable summary");
        JdbcConversationMemorySummaryService service = new JdbcConversationMemorySummaryService(
                groups, messages, properties, llm, prompts, redisson, Runnable::run);

        service.compressIfNeeded("c1", "u1", ChatMessage.assistant("latest answer"));

        ArgumentCaptor<ConversationSummaryBO> summary = ArgumentCaptor.forClass(ConversationSummaryBO.class);
        verify(messages).addMessageSummary(summary.capture());
        assertEquals("durable summary", summary.getValue().getContent());
        assertEquals("3", summary.getValue().getLastMessageId());
        verify(groups).listMessagesBetweenIds("c1", "u1", null, "4");
        verify(lock).unlock();
    }

    private ConversationMessageDO message(String id, String role, String content) {
        ConversationMessageDO result = new ConversationMessageDO();
        result.setId(id);
        result.setRole(role);
        result.setContent(content);
        return result;
    }
}
