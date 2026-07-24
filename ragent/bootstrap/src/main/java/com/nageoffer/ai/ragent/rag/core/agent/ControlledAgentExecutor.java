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
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrieveRequest;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.apache.skywalking.apm.toolkit.trace.Trace;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 受控单 Agent 状态机。所有 Planner/工具调用都经过同一硬预算；失败只返回工具摘要和 RAG 降级，
 * 不暴露内部思维过程、完整工具正文或异常消息。
 */
@Service
@ConditionalOnProperty(name = "rag.agent.enabled", havingValue = "true")
public class ControlledAgentExecutor {
    private final AgentPlanner planner;
    private final AgentToolRegistry toolRegistry;
    private final TrustedRetrievalService fallbackRetrieval;
    private final TrustedAnswerGenerator answerGenerator;
    private final CitationValidator citationValidator;
    private final ExecutorService boundedExecutor;
    private final Clock clock;

    @Autowired
    public ControlledAgentExecutor(AgentPlanner planner, AgentToolRegistry toolRegistry,
                                   TrustedRetrievalService fallbackRetrieval,
                                   TrustedAnswerGenerator answerGenerator,
                                   CitationValidator citationValidator,
                                   @Qualifier("agentBoundedExecutor") ExecutorService boundedExecutor) {
        this(planner, toolRegistry, fallbackRetrieval, answerGenerator, citationValidator, boundedExecutor,
                Clock.systemUTC());
    }

    ControlledAgentExecutor(AgentPlanner planner, AgentToolRegistry toolRegistry,
                            TrustedRetrievalService fallbackRetrieval, TrustedAnswerGenerator answerGenerator,
                            CitationValidator citationValidator,
                            ExecutorService boundedExecutor, Clock clock) {
        this.planner = planner;
        this.toolRegistry = toolRegistry;
        this.fallbackRetrieval = fallbackRetrieval;
        this.answerGenerator = answerGenerator;
        this.citationValidator = citationValidator;
        this.boundedExecutor = boundedExecutor;
        this.clock = clock;
    }

    @Trace(operationName = "rag.agent.execute")
    public Result execute(Request request) {
        if (request == null || request.question() == null || request.question().isBlank()
                || request.question().length() > 500) {
            throw new IllegalArgumentException("Agent问题长度必须在1到500之间");
        }
        AgentExecutionBudget budget = new AgentExecutionBudget(clock, request.timeoutMillis(), request.tokenBudget());
        AgentToolContext toolContext = new AgentToolContext(UserContext.getUserId());
        List<AgentObservation> observations = new ArrayList<>();
        List<ToolCallSummary> summaries = new ArrayList<>();
        try {
            while (true) {
                budget.beginStep(0);
                AgentAction action = bounded(() -> planner.next(request.question(), List.copyOf(observations), budget.view()), budget);
                if (action == null) throw new ControlledFailure("AGENT_INVALID_ACTION");
                budget.acceptPlannerTokens(action.estimatedTokens());
                if (action instanceof AgentAction.ToolCall toolCall) {
                    enforceToolPreconditions(toolCall, observations);
                    AgentToolRegistry.Invocation invocation = toolRegistry.prepare(toolCall.toolName(), toolCall.input());
                    budget.authorizeTool(invocation);
                    AgentToolResult toolResult = bounded(() -> invocation.call().execute(toolContext), budget);
                    budget.acceptToolResult(toolResult);
                    TrustedRetrievalResult retrieval = toolResult.retrieval();
                    observations.add(new AgentObservation(invocation.name(), toolResult.summary(),
                            retrieval == null ? "" : retrieval.context(), toolResult.citations()));
                    summaries.add(new ToolCallSummary(invocation.name().value(), "SUCCESS",
                            toolResult.citations().size()));
                    continue;
                }
                if (action instanceof AgentAction.FinalAnswer answer) {
                    if (!answer.requiresEvidence() && observations.isEmpty()
                            && !isSafeDirectQuestion(request.question())) {
                        throw new ControlledFailure("AGENT_DIRECT_ANSWER_NOT_ALLOWED");
                    }
                    return validateFinal(answer, observations, summaries, budget);
                }
                throw new ControlledFailure("AGENT_INVALID_ACTION");
            }
        } catch (AgentExecutionBudget.BudgetExceeded failure) {
            return fallback(request.question(), failure.code(), summaries, budget);
        } catch (ControlledFailure failure) {
            return fallback(request.question(), failure.code, summaries, budget);
        } catch (RuntimeException failure) {
            return fallback(request.question(), "AGENT_EXECUTION_FAILED", summaries, budget);
        }
    }

    private void enforceToolPreconditions(AgentAction.ToolCall toolCall, List<AgentObservation> observations) {
        if (toolCall.toolName() != AgentToolName.GET_ARTICLE_DETAIL) return;
        if (!(toolCall.input() instanceof GetArticleDetailTool.Input detail)) {
            throw new ControlledFailure("AGENT_INVALID_DETAIL_INPUT");
        }
        String expected = Long.toString(detail.articleId());
        boolean discovered = observations.stream().flatMap(item -> item.citations().stream())
                .anyMatch(citation -> expected.equals(citation.articleId()));
        if (!discovered) throw new ControlledFailure("AGENT_DETAIL_NOT_DISCOVERED");
    }

    private boolean isSafeDirectQuestion(String question) {
        String normalized = question == null ? "" : question.strip().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s，。！？!?,.]", "");
        return normalized.matches("(你好|您好|嗨|hi|hello|你是谁|你能做什么|谢谢|感谢|再见)");
    }

    private Result validateFinal(AgentAction.FinalAnswer answer, List<AgentObservation> observations,
                                 List<ToolCallSummary> summaries, AgentExecutionBudget budget) {
        if (answer.answer() == null || answer.answer().isBlank()) throw new ControlledFailure("AGENT_EMPTY_ANSWER");
        List<TrustedRetrievalResult.Citation> allowed = observations.stream()
                .flatMap(item -> item.citations().stream()).toList();
        boolean evidenceRequired = answer.requiresEvidence() || !observations.isEmpty();
        if (!evidenceRequired) {
            return new Result("DIRECT", answer.answer(), false, "", List.copyOf(summaries), List.of(), null,
                    budget.usage());
        }
        CitationValidator.Validation validation = citationValidator.validate(answer.answer(), allowed);
        if (!validation.valid()) throw new ControlledFailure(validation.code());
        Set<String> referenced = validation.referencedChunkIds();
        List<TrustedRetrievalResult.Citation> used = allowed.stream()
                .filter(citation -> referenced.contains(citation.chunkId())).toList();
        return new Result("AGENT", answer.answer(), false, "", List.copyOf(summaries), used, null,
                budget.usage());
    }

    private Result fallback(String question, String code, List<ToolCallSummary> summaries,
                            AgentExecutionBudget budget) {
        try {
            TrustedRetrievalResult retrieval = bounded(() -> fallbackRetrieval.retrieve(TrustedRetrieveRequest.builder()
                    .query(question).candidateTopK(20).topK(6).maxContextTokens(4000).build()), budget);
            budget.recordFallbackRetrieval(retrieval);
            TrustedAnswerGenerator.GeneratedAnswer generated = bounded(
                    () -> answerGenerator.generate(question, retrieval), budget);
            budget.acceptGeneratedTokens(generated.estimatedTokens(), generated.modelCalled());
            String failureCode = generated.generated() ? code : code + ":" + generated.code();
            return new Result("RAG_FALLBACK", generated.answer(), true, failureCode, List.copyOf(summaries),
                    generated.citations(), retrieval, budget.usage());
        } catch (RuntimeException ignored) {
            return new Result("CONTROLLED_FAILURE", "", true, code, List.copyOf(summaries), List.of(), null,
                    budget.usage());
        }
    }

    private <T> T bounded(Callable<T> task, AgentExecutionBudget budget) {
        long remaining = budget.remainingMillis();
        Future<T> future;
        try {
            future = boundedExecutor.submit(task);
        } catch (RuntimeException rejected) {
            throw new ControlledFailure("AGENT_EXECUTOR_SATURATED");
        }
        try {
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new AgentExecutionBudget.BudgetExceeded("AGENT_TIMEOUT");
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ControlledFailure("AGENT_INTERRUPTED");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new ControlledFailure("AGENT_EXECUTION_FAILED");
        }
    }

    public record Request(String question, long timeoutMillis, int tokenBudget) {}

    public record ToolCallSummary(String toolName, String status, int citationCount) {}

    public record Result(String mode, String answer, boolean fallback, String failureCode,
                         List<ToolCallSummary> toolCalls,
                         List<TrustedRetrievalResult.Citation> citations,
                         TrustedRetrievalResult fallbackRetrieval,
                         AgentExecutionBudget.Usage usage) {}

    private static final class ControlledFailure extends RuntimeException {
        private final String code;
        private ControlledFailure(String code) { super(code); this.code = code; }
    }
}
