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

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** 服务端强制的步数、检索次数、截止时间、Token 和重复调用状态机。 */
public final class AgentExecutionBudget {
    public static final int MAX_STEPS = 3;
    public static final int MAX_RETRIEVAL_CALLS = 2;
    public static final int MAX_TOKEN_BUDGET = 8_000;
    public static final long MAX_TIMEOUT_MILLIS = 30_000L;

    private final Clock clock;
    private final long deadlineMillis;
    private final int tokenBudget;
    private final Set<String> calledSignatures = new LinkedHashSet<>();
    private int usedSteps;
    private int toolCalls;
    private int retrievalCalls;
    private int fallbackRetrievalCalls;
    private int rerankCalls;
    private int retrievalCacheHits;
    private int modelCalls;
    private int usedTokens;

    public AgentExecutionBudget(Clock clock, long requestedTimeoutMillis, int requestedTokenBudget) {
        this.clock = clock;
        long timeout = Math.max(1L, Math.min(MAX_TIMEOUT_MILLIS, requestedTimeoutMillis));
        this.deadlineMillis = clock.millis() + timeout;
        this.tokenBudget = Math.max(1, Math.min(MAX_TOKEN_BUDGET, requestedTokenBudget));
    }

    public void beginStep(int estimatedTokens) {
        ensureTime();
        if (usedSteps >= MAX_STEPS) throw new BudgetExceeded("AGENT_STEP_LIMIT");
        usedSteps++;
        modelCalls++;
        consumeTokens(estimatedTokens);
    }

    public void authorizeTool(AgentToolRegistry.Invocation invocation) {
        ensureTime();
        if (invocation.name().retrieval() && retrievalCalls >= MAX_RETRIEVAL_CALLS) {
            throw new BudgetExceeded("AGENT_RETRIEVAL_LIMIT");
        }
        String normalized = invocation.name().value() + ":" + normalize(invocation.signature());
        if (!calledSignatures.add(normalized)) throw new BudgetExceeded("AGENT_DUPLICATE_TOOL_CALL");
        toolCalls++;
        if (invocation.name().retrieval()) retrievalCalls++;
    }

    public void acceptToolResult(AgentToolResult result) {
        ensureTime();
        consumeTokens(result.estimatedTokens());
        if (result.retrieval() != null) {
            if (result.retrieval().cacheHit()) retrievalCacheHits++;
            else if (result.retrieval().rerankApplied()) rerankCalls++;
        }
    }

    public void acceptPlannerTokens(int estimatedTokens) {
        ensureTime();
        consumeTokens(estimatedTokens);
    }

    public void recordFallbackRetrieval(com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult retrieval) {
        ensureTime();
        fallbackRetrievalCalls++;
        if (retrieval != null) {
            if (retrieval.cacheHit()) retrievalCacheHits++;
            else if (retrieval.rerankApplied()) rerankCalls++;
        }
    }

    public void acceptGeneratedTokens(int estimatedTokens, boolean modelCalled) {
        ensureTime();
        if (modelCalled) modelCalls++;
        consumeTokens(estimatedTokens);
    }

    public long remainingMillis() {
        ensureTime();
        return Math.max(1L, deadlineMillis - clock.millis());
    }

    public State view() {
        return new State(MAX_STEPS - usedSteps, MAX_RETRIEVAL_CALLS - retrievalCalls,
                tokenBudget - usedTokens, Math.max(0L, deadlineMillis - clock.millis()),
                Set.copyOf(calledSignatures));
    }

    public Usage usage() {
        return new Usage(usedSteps, toolCalls, retrievalCalls, fallbackRetrievalCalls, rerankCalls,
                retrievalCacheHits, modelCalls, usedTokens, Math.max(0L, deadlineMillis - clock.millis()));
    }

    private void consumeTokens(int amount) {
        int safe = Math.max(0, amount);
        if (usedTokens + safe > tokenBudget) throw new BudgetExceeded("AGENT_TOKEN_LIMIT");
        usedTokens += safe;
    }

    private void ensureTime() {
        if (clock.millis() >= deadlineMillis) throw new BudgetExceeded("AGENT_TIMEOUT");
    }

    private String normalize(String value) {
        return (value == null ? "" : value).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public record State(int remainingSteps, int remainingRetrievalCalls, int remainingTokens,
                        long remainingMillis, Set<String> calledToolSignatures) {}

    /** 低基数、无用户内容的请求级用量快照。 */
    public record Usage(int steps, int toolCalls, int retrievalCalls, int fallbackRetrievalCalls,
                        int rerankCalls, int retrievalCacheHits, int modelCalls,
                        int tokens, long remainingMillis) {
        public Usage(int steps, int retrievalCalls, int tokens, long remainingMillis) {
            this(steps, 0, retrievalCalls, 0, 0, 0, steps, tokens, remainingMillis);
        }

        public Usage(int steps, int toolCalls, int retrievalCalls, int fallbackRetrievalCalls,
                     int rerankCalls, int modelCalls, int tokens, long remainingMillis) {
            this(steps, toolCalls, retrievalCalls, fallbackRetrievalCalls,
                    rerankCalls, 0, modelCalls, tokens, remainingMillis);
        }

        public int totalRetrievalCalls() {
            return retrievalCalls + fallbackRetrievalCalls;
        }
    }

    public static final class BudgetExceeded extends RuntimeException {
        private final String code;
        public BudgetExceeded(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
}
