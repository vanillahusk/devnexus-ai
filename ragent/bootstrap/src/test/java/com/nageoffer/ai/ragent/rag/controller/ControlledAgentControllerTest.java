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

import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.rag.core.agent.ControlledAgentExecutor;
import com.nageoffer.ai.ragent.rag.core.agent.quota.AgentQuotaService;
import com.nageoffer.ai.ragent.rag.core.agent.usage.AgentCostProperties;
import com.nageoffer.ai.ragent.rag.core.agent.usage.AgentUsageAccountingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.nageoffer.ai.ragent.rag.observability.RagMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControlledAgentControllerTest {
    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void shouldRequireLoginAndForceServerSideBudgets() {
        ControlledAgentExecutor executor = mock(ControlledAgentExecutor.class);
        AgentQuotaService quotaService = mock(AgentQuotaService.class);
        ControlledAgentController controller = new ControlledAgentController(executor, quotaService, accounting(), metrics());
        assertThrows(RuntimeException.class,
                () -> controller.query(new ControlledAgentController.AgentRequest("你好", "session-1")));

        UserContext.set(LoginUser.builder().userId("u-1").username("user").build());
        when(executor.execute(org.mockito.ArgumentMatchers.any())).thenReturn(
                new ControlledAgentExecutor.Result("DIRECT", "你好", false, "", List.of(), List.of(), null,
                        new com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget.Usage(1, 0, 5, 14_000)));
        AgentQuotaService.Reservation reservation = AgentQuotaService.Reservation.disabled();
        when(quotaService.reserve("u-1", "session-1", 3, 8_000)).thenReturn(reservation);
        var response = controller.query(new ControlledAgentController.AgentRequest("你好", "session-1"));

        assertEquals("你好", response.getData().answer());
        ArgumentCaptor<ControlledAgentExecutor.Request> request =
                ArgumentCaptor.forClass(ControlledAgentExecutor.Request.class);
        verify(executor).execute(request.capture());
        assertEquals(30_000L, request.getValue().timeoutMillis());
        assertEquals(8_000, request.getValue().tokenBudget());
        assertFalse(response.getData().fallback());
        assertEquals(5, response.getData().usage().estimatedTokens());
        verify(quotaService).settle(reservation,
                new com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget.Usage(1, 0, 5, 14_000));
    }

    @Test
    void shouldRejectUnsafeSessionIdBeforeQuotaReservation() {
        ControlledAgentExecutor executor = mock(ControlledAgentExecutor.class);
        AgentQuotaService quotaService = mock(AgentQuotaService.class);
        ControlledAgentController controller = new ControlledAgentController(executor, quotaService, accounting(), metrics());
        UserContext.set(LoginUser.builder().userId("u-1").build());

        assertThrows(RuntimeException.class,
                () -> controller.query(new ControlledAgentController.AgentRequest("你好", "../unsafe")));
        org.mockito.Mockito.verifyNoInteractions(quotaService, executor);
    }

    @Test
    void shouldSettleReservationWhenAgentExecutionFails() {
        ControlledAgentExecutor executor = mock(ControlledAgentExecutor.class);
        AgentQuotaService quotaService = mock(AgentQuotaService.class);
        AgentQuotaService.Reservation reservation = AgentQuotaService.Reservation.disabled();
        ControlledAgentController controller = new ControlledAgentController(executor, quotaService, accounting(), metrics());
        UserContext.set(LoginUser.builder().userId("u-1").build());
        when(quotaService.reserve("u-1", "session-1", 3, 8_000)).thenReturn(reservation);
        when(executor.execute(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("failed"));

        assertThrows(IllegalStateException.class,
                () -> controller.query(new ControlledAgentController.AgentRequest("问题", "session-1")));

        verify(quotaService).settle(reservation, null);
    }

    private AgentUsageAccountingService accounting() {
        AgentCostProperties properties = new AgentCostProperties();
        properties.setModelName("test-model");
        properties.setEstimatedMicrosPerMillionTokens(1_000_000);
        return new AgentUsageAccountingService(properties);
    }

    private RagMetrics metrics() {
        return new RagMetrics(new SimpleMeterRegistry());
    }
}
