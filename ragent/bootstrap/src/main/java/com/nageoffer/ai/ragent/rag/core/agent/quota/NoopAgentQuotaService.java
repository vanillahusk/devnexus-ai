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

package com.nageoffer.ai.ragent.rag.core.agent.quota;

import com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 仅供显式关闭配额的本地环境使用；生产默认启用 Redis 配额。 */
@Service
@ConditionalOnProperty(name = "rag.agent.quota.enabled", havingValue = "false")
public class NoopAgentQuotaService implements AgentQuotaService {
    @Override
    public Reservation reserve(String userId, String sessionId, int steps, int tokens) {
        return Reservation.disabled();
    }

    @Override
    public void settle(Reservation reservation, AgentExecutionBudget.Usage actualUsage) {
        // Explicitly disabled.
    }
}
