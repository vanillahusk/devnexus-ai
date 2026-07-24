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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将低敏请求级计数转为稳定报告；不接收 userId、sessionId 或 query。 */
@Service
@RequiredArgsConstructor
public class AgentUsageAccountingService {
    private static final long ONE_MILLION = 1_000_000L;
    private final AgentCostProperties properties;

    public UsageReport summarize(AgentExecutionBudget.Usage usage) {
        if (usage == null) return UsageReport.empty(properties.getModelName());
        long product = Math.multiplyExact((long) usage.tokens(), properties.getEstimatedMicrosPerMillionTokens());
        long estimatedCostMicros = product == 0 ? 0 : Math.addExact(product, ONE_MILLION - 1) / ONE_MILLION;
        return new UsageReport(usage.steps(), usage.toolCalls(), usage.totalRetrievalCalls(),
                usage.retrievalCalls(), usage.fallbackRetrievalCalls(), usage.rerankCalls(), usage.modelCalls(),
                usage.retrievalCacheHits(), usage.tokens(), estimatedCostMicros,
                properties.getEstimatedMicrosPerMillionTokens() > 0,
                properties.getModelName(), usage.remainingMillis());
    }

    public record UsageReport(int steps, int toolCalls, int retrievalCalls, int agentRetrievalCalls,
                              int fallbackRetrievalCalls, int rerankCalls, int modelCalls,
                              int retrievalCacheHits, int estimatedTokens,
                              long estimatedCostMicros, boolean costConfigured,
                              String modelName, long remainingMillis) {
        private static UsageReport empty(String modelName) {
            return new UsageReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, modelName, 0);
        }
    }
}
