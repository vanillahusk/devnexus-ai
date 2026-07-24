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
import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.rag.enums.ConversationMessageOrder;
import com.nageoffer.ai.ragent.rag.service.ConversationMessageService;
import com.nageoffer.ai.ragent.rag.service.ConversationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcConversationMemoryStoreTest {

    @Test
    void shouldLoadOnlyConfiguredRecentWindowAndDropLeadingOrphanAssistant() {
        ConversationService conversations = mock(ConversationService.class);
        ConversationMessageService messages = mock(ConversationMessageService.class);
        MemoryProperties properties = new MemoryProperties();
        properties.setHistoryKeepTurns(4);
        JdbcConversationMemoryStore store = new JdbcConversationMemoryStore(conversations, messages, properties);

        when(messages.listMessages("c1", "u1", 8, ConversationMessageOrder.DESC)).thenReturn(List.of(
                message("assistant", "orphan"),
                message("user", "question"),
                message("assistant", "answer")
        ));

        List<ChatMessage> history = store.loadHistory("c1", "u1");

        verify(messages).listMessages("c1", "u1", 8, ConversationMessageOrder.DESC);
        assertEquals(2, history.size());
        assertEquals(ChatMessage.Role.USER, history.get(0).getRole());
        assertEquals("question", history.get(0).getContent());
        assertEquals(ChatMessage.Role.ASSISTANT, history.get(1).getRole());
    }

    private ConversationMessageVO message(String role, String content) {
        ConversationMessageVO result = new ConversationMessageVO();
        result.setRole(role);
        result.setContent(content);
        return result;
    }
}
