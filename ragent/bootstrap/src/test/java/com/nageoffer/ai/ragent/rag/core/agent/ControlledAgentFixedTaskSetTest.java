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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 固定任务只验证服务端状态机路径，不作为真实 LLM Planner 准确率证据。 */
class ControlledAgentFixedTaskSetTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("tasks")
    void shouldConvergeFixedControlFlowTask(Task task) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            ControlledAgentExecutor executor = executor(planner(task.path()), pool);
            ControlledAgentExecutor.Result result = executor.execute(
                    new ControlledAgentExecutor.Request(task.question(), 5_000, 2_000));

            assertEquals(task.expectedMode(), result.mode());
            String actualTools = result.toolCalls().isEmpty() ? "-" : result.toolCalls().stream()
                    .map(ControlledAgentExecutor.ToolCallSummary::toolName)
                    .collect(java.util.stream.Collectors.joining(","));
            assertEquals(task.expectedTools(), actualTools);
            if (!"-".equals(task.expectedFailurePrefix())) {
                assertTrue(result.failureCode().startsWith(task.expectedFailurePrefix()));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private AgentPlanner planner(String path) {
        return switch (path) {
            case "DIRECT" -> (question, observations, state) -> new AgentAction.FinalAnswer("你好", false, 2);
            case "SEARCH" -> (question, observations, state) -> observations.isEmpty()
                    ? new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE, "RocketMQ恢复", 5)
                    : new AgentAction.FinalAnswer("使用重试恢复。[ref:c1]", true, 5);
            case "REWRITE" -> (question, observations, state) -> switch (observations.size()) {
                case 0 -> new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE, "消息失败", 5);
                case 1 -> new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE, "RocketMQ重试DLQ", 5);
                default -> new AgentAction.FinalAnswer("失败重试后进入死信。[ref:c1]", true, 5);
            };
            case "DETAIL" -> (question, observations, state) -> switch (observations.size()) {
                case 0 -> new AgentAction.ToolCall(AgentToolName.SEARCH_KNOWLEDGE, "文章1003", 5);
                case 1 -> new AgentAction.ToolCall(AgentToolName.GET_ARTICLE_DETAIL,
                        new GetArticleDetailTool.Input(1003), 5);
                default -> new AgentAction.FinalAnswer("文章详情证据。[ref:d1]", true, 5);
            };
            case "DETAIL_BLOCKED" -> (question, observations, state) -> new AgentAction.ToolCall(
                    AgentToolName.GET_ARTICLE_DETAIL, new GetArticleDetailTool.Input(9999), 5);
            case "DUPLICATE" -> (question, observations, state) -> new AgentAction.ToolCall(
                    AgentToolName.SEARCH_KNOWLEDGE, observations.isEmpty() ? " Redis   Queue " : "redis queue", 5);
            case "PLANNER_FAILURE" -> (question, observations, state) -> {
                throw new IllegalStateException("private planner detail");
            };
            default -> throw new IllegalArgumentException("unknown path");
        };
    }

    private ControlledAgentExecutor executor(AgentPlanner planner, ExecutorService pool) {
        TrustedRetrievalService fallback = mock(TrustedRetrievalService.class);
        when(fallback.retrieve(any())).thenReturn(retrieval("c1", "1003"));
        TrustedAnswerGenerator generator = mock(TrustedAnswerGenerator.class);
        when(generator.generate(any(), any())).thenReturn(new TrustedAnswerGenerator.GeneratedAnswer(
                "安全降级回答。[ref:c1]", true, true, "OK", 5, List.of(citation("c1", "1003"))));
        return new ControlledAgentExecutor(planner,
                new AgentToolRegistry(List.of(new SearchTool(), new DetailTool())), fallback, generator,
                new CitationValidator(), pool, Clock.systemUTC());
    }

    private TrustedRetrievalResult retrieval(String chunkId, String articleId) {
        return new TrustedRetrievalResult(true, "FIXTURE", Map.of(), false, 10,
                "<untrusted_documents>fixture</untrusted_documents>", List.of(citation(chunkId, articleId)));
    }

    private TrustedRetrievalResult.Citation citation(String chunkId, String articleId) {
        return new TrustedRetrievalResult.Citation(chunkId, articleId, "8", "title", "heading",
                "snippet", 0.1F, null);
    }

    private final class SearchTool implements AgentTool<String> {
        @Override public AgentToolName name() { return AgentToolName.SEARCH_KNOWLEDGE; }
        @Override public Class<String> inputType() { return String.class; }
        @Override public String normalizedSignature(String input) { return input; }
        @Override public AgentToolResult execute(String input) {
            var retrieval = retrieval("c1", "1003");
            return new AgentToolResult("citations=1", 10, retrieval.citations(), retrieval);
        }
    }

    private final class DetailTool implements AgentTool<GetArticleDetailTool.Input> {
        @Override public AgentToolName name() { return AgentToolName.GET_ARTICLE_DETAIL; }
        @Override public Class<GetArticleDetailTool.Input> inputType() { return GetArticleDetailTool.Input.class; }
        @Override public String normalizedSignature(GetArticleDetailTool.Input input) {
            return Long.toString(input.articleId());
        }
        @Override public AgentToolResult execute(GetArticleDetailTool.Input input) {
            var retrieval = retrieval("d1", Long.toString(input.articleId()));
            return new AgentToolResult("citations=1", 10, retrieval.citations(), retrieval);
        }
    }

    static Stream<Task> tasks() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                ControlledAgentFixedTaskSetTest.class.getResourceAsStream("/rag/agent-fixed-tasks.tsv"),
                StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank()).map(line -> {
                String[] values = line.split("\\t", -1);
                return new Task(values[0], values[1], values[2], values[3], values[4], values[5]);
            }).toList().stream();
        }
    }

    record Task(String id, String path, String question, String expectedMode,
                String expectedTools, String expectedFailurePrefix) {
        @Override public String toString() { return id + ":" + path; }
    }
}
