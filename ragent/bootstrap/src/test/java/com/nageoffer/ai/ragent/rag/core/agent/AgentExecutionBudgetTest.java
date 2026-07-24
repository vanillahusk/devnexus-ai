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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExecutionBudgetTest {
    @Test
    void shouldEnforceThreeStepsAndTwoRetrievalCalls() {
        MutableClock clock = new MutableClock();
        AgentExecutionBudget budget = new AgentExecutionBudget(clock, 15_000, 8_000);
        AgentToolRegistry registry = new AgentToolRegistry(java.util.List.of(new StubTool()));

        for (int i = 0; i < 2; i++) {
            budget.beginStep(10);
            AgentToolRegistry.Invocation invocation = registry.prepare(AgentToolName.SEARCH_KNOWLEDGE, "q" + i);
            budget.authorizeTool(invocation);
            budget.acceptToolResult(new AgentToolResult("ok", 20, java.util.List.of(), null));
        }
        budget.beginStep(10);

        AgentExecutionBudget.BudgetExceeded step = assertThrows(AgentExecutionBudget.BudgetExceeded.class,
                () -> budget.beginStep(1));
        AgentExecutionBudget.BudgetExceeded retrieval = assertThrows(AgentExecutionBudget.BudgetExceeded.class,
                () -> budget.authorizeTool(registry.prepare(AgentToolName.SEARCH_KNOWLEDGE, "q3")));
        assertEquals("AGENT_STEP_LIMIT", step.code());
        assertEquals("AGENT_RETRIEVAL_LIMIT", retrieval.code());
        AgentExecutionBudget.Usage usage = budget.usage();
        assertEquals(3, usage.steps());
        assertEquals(2, usage.toolCalls());
        assertEquals(2, usage.retrievalCalls());
        assertEquals(3, usage.modelCalls());
        assertEquals(70, usage.tokens());
    }

    @Test
    void shouldNormalizeEquivalentToolCallsAndRejectLoop() {
        AgentExecutionBudget budget = new AgentExecutionBudget(new MutableClock(), 15_000, 8_000);
        AgentToolRegistry registry = new AgentToolRegistry(java.util.List.of(new StubTool()));
        budget.authorizeTool(registry.prepare(AgentToolName.SEARCH_KNOWLEDGE, "  Redis   Queue "));

        AgentExecutionBudget.BudgetExceeded duplicate = assertThrows(AgentExecutionBudget.BudgetExceeded.class,
                () -> budget.authorizeTool(registry.prepare(AgentToolName.SEARCH_KNOWLEDGE, "redis queue")));

        assertEquals("AGENT_DUPLICATE_TOOL_CALL", duplicate.code());
    }

    @Test
    void shouldEnforceDeadlineAndTokenBudget() {
        MutableClock clock = new MutableClock();
        AgentExecutionBudget budget = new AgentExecutionBudget(clock, 100, 50);
        budget.beginStep(40);
        assertEquals("AGENT_TOKEN_LIMIT", assertThrows(AgentExecutionBudget.BudgetExceeded.class,
                () -> budget.beginStep(11)).code());

        MutableClock expiredClock = new MutableClock();
        AgentExecutionBudget expired = new AgentExecutionBudget(expiredClock, 100, 50);
        expiredClock.advance(100);
        assertEquals("AGENT_TIMEOUT", assertThrows(AgentExecutionBudget.BudgetExceeded.class,
                () -> expired.beginStep(1)).code());
    }

    @Test
    void shouldCountFallbackRetrievalRerankAndGenerationModelCall() {
        AgentExecutionBudget budget = new AgentExecutionBudget(new MutableClock(), 15_000, 8_000);
        var retrieval = new com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult(
                true, "OK", java.util.Map.of(), true, 10, "context", java.util.List.of());

        budget.recordFallbackRetrieval(retrieval);
        budget.acceptGeneratedTokens(120, true);

        AgentExecutionBudget.Usage usage = budget.usage();
        assertEquals(1, usage.fallbackRetrievalCalls());
        assertEquals(1, usage.totalRetrievalCalls());
        assertEquals(1, usage.rerankCalls());
        assertEquals(1, usage.modelCalls());
        assertEquals(120, usage.tokens());
    }

    @Test
    void shouldCountCacheHitWithoutClaimingAnotherRerankCall() {
        AgentExecutionBudget budget = new AgentExecutionBudget(new MutableClock(), 15_000, 8_000);
        var cached = new com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult(
                true, "OK", java.util.Map.of(), true, true, 10, "context", java.util.List.of());

        budget.recordFallbackRetrieval(cached);

        assertEquals(1, budget.usage().retrievalCacheHits());
        assertEquals(0, budget.usage().rerankCalls());
    }

    private static final class StubTool implements AgentTool<String> {
        @Override public AgentToolName name() { return AgentToolName.SEARCH_KNOWLEDGE; }
        @Override public Class<String> inputType() { return String.class; }
        @Override public String normalizedSignature(String input) { return input; }
        @Override public AgentToolResult execute(String input) { return new AgentToolResult("ok", 1, java.util.List.of(), null); }
    }

    private static final class MutableClock extends Clock {
        private long millis;
        void advance(long amount) { millis += amount; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
