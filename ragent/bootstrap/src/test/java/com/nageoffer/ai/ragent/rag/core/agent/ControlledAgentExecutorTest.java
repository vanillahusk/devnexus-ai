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

import com.nageoffer.ai.ragent.rag.core.prompt.CitationValidator;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControlledAgentExecutorTest {
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() {
        executorService.shutdownNow();
    }

    @Test
    void shouldExecuteOneSearchAndReturnOnlyReferencedCitationWithoutThoughtProcess() {
        AgentPlanner planner = (question, observations, state) -> observations.isEmpty()
                ? new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE, "Redis", 20)
                : new AgentAction.FinalAnswer("使用processing保存处理中任务。[ref:c1]", true, 40);
        ControlledAgentExecutor executor = newExecutor(planner, new StubSearchTool("Redis"), fallbackResult());

        ControlledAgentExecutor.Result result = executor.execute(
                new ControlledAgentExecutor.Request("任务取出后宕机怎么办", 5_000, 1_000));

        assertEquals("AGENT", result.mode());
        assertFalse(result.fallback());
        assertEquals(1, result.toolCalls().size());
        assertEquals("searchKnowledge", result.toolCalls().get(0).toolName());
        assertEquals(List.of("c1"), result.citations().stream().map(TrustedRetrievalResult.Citation::chunkId).toList());
        assertFalse(result.answer().toLowerCase().contains("thought"));
    }

    @Test
    void shouldStopEquivalentRepeatedToolCallAndFallbackToTrustedRag() {
        AgentPlanner looping = (question, observations, state) -> new AgentAction.ToolCall(
                AgentToolName.SEARCH_KNOWLEDGE,
                observations.isEmpty() ? "  Redis   Queue " : "redis queue", 10);
        ControlledAgentExecutor executor = newExecutor(looping, new StubSearchTool("ignored"), fallbackResult());

        ControlledAgentExecutor.Result result = executor.execute(
                new ControlledAgentExecutor.Request("Redis队列", 5_000, 1_000));

        assertEquals("RAG_FALLBACK", result.mode());
        assertTrue(result.fallback());
        assertEquals("AGENT_DUPLICATE_TOOL_CALL", result.failureCode());
        assertEquals(1, result.toolCalls().size());
        assertTrue(result.fallbackRetrieval().answerable());
    }

    @Test
    void shouldRejectArticleDetailIdThatWasNotDiscoveredByPreviousEvidence() {
        AgentPlanner planner = (question, observations, state) -> new AgentAction.ToolCall(
                AgentToolName.GET_ARTICLE_DETAIL, new GetArticleDetailTool.Input(9999), 10);
        ControlledAgentExecutor executor = newExecutor(planner, new StubSearchTool("unused"), fallbackResult());

        ControlledAgentExecutor.Result result = executor.execute(
                new ControlledAgentExecutor.Request("展开文章", 5_000, 1_000));

        assertEquals("RAG_FALLBACK", result.mode());
        assertEquals("AGENT_DETAIL_NOT_DISCOVERED", result.failureCode());
        assertTrue(result.toolCalls().isEmpty());
    }

    @Test
    void shouldOnlyAllowDirectAnswerForServerRecognizedConversationQuestion() {
        AgentPlanner direct = (question, observations, state) ->
                new AgentAction.FinalAnswer("直接回答", false, 5);
        ControlledAgentExecutor executor = newExecutor(direct, new StubSearchTool("unused"), fallbackResult());

        assertEquals("RAG_FALLBACK", executor.execute(
                new ControlledAgentExecutor.Request("RocketMQ为什么可靠", 5_000, 1_000)).mode());
        assertEquals("DIRECT", executor.execute(
                new ControlledAgentExecutor.Request("你好！", 5_000, 1_000)).mode());
    }

    @Test
    void shouldAllowOneQueryRewriteThenFinishWithinThreeSteps() {
        AgentPlanner planner = (question, observations, state) -> switch (observations.size()) {
            case 0 -> new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE, "RocketMQ可靠性", 5);
            case 1 -> new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE, "RocketMQ重试DLQ", 5);
            default -> new AgentAction.FinalAnswer("失败会重试并进入死信。[ref:c1]", true, 10);
        };
        ControlledAgentExecutor executor = newExecutor(planner, new StubSearchTool("unused"), fallbackResult());

        ControlledAgentExecutor.Result result = executor.execute(
                new ControlledAgentExecutor.Request("RocketMQ失败怎么办", 5_000, 1_000));

        assertEquals("AGENT", result.mode());
        assertEquals(2, result.toolCalls().size());
    }

    @Test
    void shouldFallbackWithoutLeakingPlannerException() {
        AgentPlanner broken = (question, observations, state) -> { throw new IllegalStateException("secret prompt"); };
        ControlledAgentExecutor executor = newExecutor(broken, new StubSearchTool("unused"), fallbackResult());

        ControlledAgentExecutor.Result result = executor.execute(
                new ControlledAgentExecutor.Request("知识问题", 5_000, 1_000));

        assertEquals("RAG_FALLBACK", result.mode());
        assertFalse(result.failureCode().contains("secret"));
        assertFalse(result.answer().isBlank());
    }

    private ControlledAgentExecutor newExecutor(AgentPlanner planner, AgentTool<?> tool,
                                                 TrustedRetrievalResult fallback) {
        TrustedRetrievalService fallbackService = mock(TrustedRetrievalService.class);
        when(fallbackService.retrieve(any())).thenReturn(fallback);
        TrustedAnswerGenerator answerGenerator = mock(TrustedAnswerGenerator.class);
        when(answerGenerator.generate(any(), any())).thenReturn(new TrustedAnswerGenerator.GeneratedAnswer(
                "降级回答。[ref:c1]", true, true, "OK", 10, List.of(citation())));
        return new ControlledAgentExecutor(planner, new AgentToolRegistry(List.of(tool)), fallbackService, answerGenerator,
                new CitationValidator(), executorService, Clock.systemUTC());
    }

    private TrustedRetrievalResult fallbackResult() {
        TrustedRetrievalResult.Citation citation = citation();
        return new TrustedRetrievalResult(true, "LEXICAL_EVIDENCE", Map.of(), false, 30,
                "<untrusted_documents>evidence</untrusted_documents>", List.of(citation));
    }

    private TrustedRetrievalResult.Citation citation() {
        return new TrustedRetrievalResult.Citation("c1", "1003", "1", "Redis可靠队列", "恢复",
                "processing恢复", 0.1F, null);
    }

    private final class StubSearchTool implements AgentTool<String> {
        private final String ignored;
        private StubSearchTool(String ignored) { this.ignored = ignored; }
        @Override public AgentToolName name() { return AgentToolName.SEARCH_KNOWLEDGE; }
        @Override public Class<String> inputType() { return String.class; }
        @Override public String normalizedSignature(String input) { return input; }
        @Override public AgentToolResult execute(String input) {
            TrustedRetrievalResult retrieval = fallbackResult();
            return new AgentToolResult("answerable=true,citations=1", 30, List.of(citation()), retrieval);
        }
    }
}
