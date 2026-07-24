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

package com.nageoffer.ai.ragent.rag.controller;

import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget;
import com.nageoffer.ai.ragent.rag.core.agent.ControlledAgentExecutor;
import com.nageoffer.ai.ragent.rag.core.agent.quota.AgentQuotaService;
import com.nageoffer.ai.ragent.rag.core.agent.usage.AgentUsageAccountingService;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import com.nageoffer.ai.ragent.rag.observability.RagMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

/** 默认关闭的受控单 Agent 入口；响应不会序列化工具正文、Prompt 或内部思维过程。 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.agent.enabled", havingValue = "true")
public class ControlledAgentController {
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private final ControlledAgentExecutor executor;
    private final AgentQuotaService quotaService;
    private final AgentUsageAccountingService usageAccountingService;
    private final RagMetrics ragMetrics;

    @PostMapping("/rag/agent/query")
    public Result<AgentResponse> query(@RequestBody AgentRequest request) {
        String userId = UserContext.requireUser().getUserId();
        if (request == null || request.question() == null || request.question().isBlank()
                || request.question().length() > 500) {
            throw new ClientException("Agent问题长度必须在1到500之间");
        }
        if (request.sessionId() == null || !SAFE_SESSION_ID.matcher(request.sessionId()).matches()) {
            throw new ClientException("Agent sessionId格式非法");
        }
        AgentQuotaService.Reservation reservation = quotaService.reserve(userId, request.sessionId(),
                AgentExecutionBudget.MAX_STEPS, AgentExecutionBudget.MAX_TOKEN_BUDGET);
        ControlledAgentExecutor.Result result = null;
        try {
            result = executor.execute(new ControlledAgentExecutor.Request(
                    request.question(), AgentExecutionBudget.MAX_TIMEOUT_MILLIS,
                    AgentExecutionBudget.MAX_TOKEN_BUDGET));
            ragMetrics.agentResult(result);
        } finally {
            quotaService.settle(reservation, result == null ? null : result.usage());
        }
        return Results.success(new AgentResponse(result.mode(), result.answer(), result.fallback(),
                result.failureCode(), result.toolCalls(), result.citations(),
                usageAccountingService.summarize(result.usage())));
    }

    public record AgentRequest(String question, String sessionId) {}

    public record AgentResponse(String mode, String answer, boolean fallback, String failureCode,
                                List<ControlledAgentExecutor.ToolCallSummary> toolCalls,
                                List<TrustedRetrievalResult.Citation> citations,
                                AgentUsageAccountingService.UsageReport usage) {}
}
