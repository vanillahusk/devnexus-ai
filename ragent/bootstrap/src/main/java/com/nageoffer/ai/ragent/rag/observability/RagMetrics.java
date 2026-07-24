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

package com.nageoffer.ai.ragent.rag.observability;

import com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget;
import com.nageoffer.ai.ragent.rag.core.agent.ControlledAgentExecutor;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Supplier;

/** RAG/Agent 低基数业务指标，不接受身份、文章、查询或事件字段。 */
@Component
public class RagMetrics {

    private final MeterRegistry registry;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T stage(String stage, Supplier<T> action) {
        String safeStage = safeStage(stage);
        Timer.Sample sample = Timer.start(registry);
        String status = "failure";
        try {
            T result = action.get();
            status = "success";
            if (result instanceof Collection<?> values) {
                DistributionSummary.builder("rag.retrieval.results")
                        .tag("stage", safeStage)
                        .register(registry)
                        .record(values.size());
            }
            return result;
        } finally {
            sample.stop(Timer.builder("rag.retrieval.stage.duration")
                    .tag("stage", safeStage)
                    .tag("status", status)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    public void degraded(String stage) {
        Counter.builder("rag.retrieval.degraded")
                .tag("stage", safeStage(stage))
                .register(registry)
                .increment();
    }

    public void trustedResult(TrustedRetrievalResult result) {
        String outcome = result.answerable() ? "answerable" : "refused";
        Counter.builder("rag.retrieval.requests")
                .tag("outcome", outcome)
                .tag("cache", result.cacheHit() ? "hit" : "miss")
                .register(registry)
                .increment();
        DistributionSummary.builder("rag.retrieval.citations")
                .tag("outcome", outcome)
                .register(registry)
                .record(result.citations().size());
    }

    public void agentResult(ControlledAgentExecutor.Result result) {
        Counter.builder("rag.agent.requests")
                .tag("mode", safeMode(result.mode()))
                .tag("outcome", result.fallback() ? "fallback" : "success")
                .register(registry)
                .increment();
        String termination = termination(result.failureCode());
        if (!"none".equals(termination)) {
            Counter.builder("rag.agent.terminations")
                    .tag("reason", termination)
                    .register(registry)
                    .increment();
        }
        AgentExecutionBudget.Usage usage = result.usage();
        if (usage == null) return;
        summary("rag.agent.steps", usage.steps());
        summary("rag.agent.tool.calls", usage.toolCalls());
        summary("rag.agent.retrieval.calls", usage.totalRetrievalCalls());
        summary("rag.agent.model.calls", usage.modelCalls());
        summary("rag.agent.tokens", usage.tokens());
    }

    private void summary(String name, int value) {
        DistributionSummary.builder(name).register(registry).record(Math.max(0, value));
    }

    private String safeStage(String stage) {
        return switch (stage) {
            case "dense", "keyword", "fusion", "rerank", "trusted" -> stage;
            default -> "other";
        };
    }

    private String safeMode(String mode) {
        if (mode == null) return "unknown";
        return switch (mode.toUpperCase(Locale.ROOT)) {
            case "DIRECT" -> "direct";
            case "AGENT" -> "agent";
            case "RAG_FALLBACK" -> "rag_fallback";
            case "CONTROLLED_FAILURE" -> "controlled_failure";
            default -> "unknown";
        };
    }

    private String termination(String code) {
        if (code == null || code.isBlank()) return "none";
        if (code.startsWith("AGENT_DUPLICATE_TOOL_CALL")) return "duplicate_loop";
        if (code.startsWith("AGENT_STEP_LIMIT")) return "step_limit";
        if (code.startsWith("AGENT_RETRIEVAL_LIMIT")) return "retrieval_limit";
        if (code.startsWith("AGENT_TOKEN_LIMIT")) return "token_limit";
        if (code.startsWith("AGENT_TIMEOUT")) return "timeout";
        if (code.startsWith("AGENT_EXECUTOR_SATURATED")) return "executor_saturated";
        return "controlled_failure";
    }
}
