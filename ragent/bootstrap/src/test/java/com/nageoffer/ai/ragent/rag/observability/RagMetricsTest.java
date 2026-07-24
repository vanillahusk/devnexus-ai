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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagMetricsTest {

    @Test
    void shouldRecordRetrievalAndAgentMetricsWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RagMetrics metrics = new RagMetrics(registry);
        metrics.stage("keyword", () -> List.of("a", "b"));
        metrics.degraded("dense");
        metrics.trustedResult(new TrustedRetrievalResult(false, "NO_EVIDENCE", Map.of(), false,
                false, 0, "", List.of()));
        AgentExecutionBudget.Usage usage = new AgentExecutionBudget.Usage(
                3, 2, 1, 1, 1, 0, 2, 100, 500);
        metrics.agentResult(new ControlledAgentExecutor.Result("RAG_FALLBACK", "", true,
                "AGENT_DUPLICATE_TOOL_CALL", List.of(), List.of(), null, usage));

        assertEquals(1L, registry.get("rag.retrieval.stage.duration")
                .tags("stage", "keyword", "status", "success").timer().count());
        assertEquals(2D, registry.get("rag.retrieval.results").tag("stage", "keyword").summary().totalAmount());
        assertEquals(1D, registry.get("rag.retrieval.degraded").tag("stage", "dense").counter().count());
        assertEquals(1D, registry.get("rag.retrieval.requests")
                .tags("outcome", "refused", "cache", "miss").counter().count());
        assertEquals(1D, registry.get("rag.agent.terminations")
                .tag("reason", "duplicate_loop").counter().count());
        assertEquals(100D, registry.get("rag.agent.tokens").summary().totalAmount());
    }

    @Test
    void shouldCollapseUnknownLabelsInsteadOfCreatingUnboundedSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RagMetrics metrics = new RagMetrics(registry);

        metrics.stage("query-user-123", List::of);
        metrics.degraded("article-999");

        assertEquals(1L, registry.get("rag.retrieval.stage.duration")
                .tags("stage", "other", "status", "success").timer().count());
        assertEquals(1D, registry.get("rag.retrieval.degraded")
                .tag("stage", "other").counter().count());
    }
}
