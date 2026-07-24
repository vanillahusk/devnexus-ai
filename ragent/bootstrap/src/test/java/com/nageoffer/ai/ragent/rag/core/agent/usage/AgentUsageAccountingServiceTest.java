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

package com.nageoffer.ai.ragent.rag.core.agent.usage;

import com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentUsageAccountingServiceTest {
    @Test
    void shouldSummarizeLowCardinalityUsageAndRoundEstimatedCostUp() {
        AgentCostProperties properties = new AgentCostProperties();
        properties.setModelName("qwen-test");
        properties.setEstimatedMicrosPerMillionTokens(250_000);
        AgentUsageAccountingService service = new AgentUsageAccountingService(properties);

        var report = service.summarize(new AgentExecutionBudget.Usage(2, 1, 1, 1, 2, 3, 101, 5_000));

        assertEquals(2, report.retrievalCalls());
        assertEquals(1, report.agentRetrievalCalls());
        assertEquals(1, report.fallbackRetrievalCalls());
        assertEquals(2, report.rerankCalls());
        assertEquals(3, report.modelCalls());
        assertEquals(0, report.retrievalCacheHits());
        assertEquals(26, report.estimatedCostMicros());
        assertEquals("qwen-test", report.modelName());
        assertTrue(report.costConfigured());
    }
}
