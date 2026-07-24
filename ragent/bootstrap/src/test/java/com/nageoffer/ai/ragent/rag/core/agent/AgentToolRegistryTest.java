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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolRegistryTest {
    @Test
    void shouldRejectUnknownParameterTypeAndUnregisteredWhitelistedTool() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(new ReadOnlyStub()));

        assertThrows(IllegalArgumentException.class,
                () -> registry.prepare(AgentToolName.SEARCH_KNOWLEDGE, 123));
        assertThrows(IllegalArgumentException.class,
                () -> registry.prepare(AgentToolName.GET_ARTICLE_DETAIL, "1001"));
    }

    @Test
    void shouldRejectWriteToolEvenIfItsNameIsWhitelisted() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new AgentToolRegistry(List.of(new WriteStub())));
        assertEquals("Agent第一版禁止写工具: searchKnowledge", failure.getMessage());
    }

    private static class ReadOnlyStub implements AgentTool<String> {
        @Override public AgentToolName name() { return AgentToolName.SEARCH_KNOWLEDGE; }
        @Override public Class<String> inputType() { return String.class; }
        @Override public String normalizedSignature(String input) { return input; }
        @Override public AgentToolResult execute(String input) { return new AgentToolResult("ok", 1, List.of(), null); }
    }

    private static final class WriteStub extends ReadOnlyStub {
        @Override public boolean readOnly() { return false; }
    }
}
