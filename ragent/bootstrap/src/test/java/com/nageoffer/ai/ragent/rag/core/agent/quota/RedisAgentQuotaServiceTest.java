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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAgentQuotaServiceTest {
    @Test
    void shouldReserveWorstCaseAndSettleActualUsageWithoutRawIdentityInKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L, 1L);
        RedisAgentQuotaService service = service(redis);

        AgentQuotaService.Reservation reservation = service.reserve("user@example.com", "session-1", 3, 8_000);
        service.settle(reservation, new AgentExecutionBudget.Usage(1, 1, 120, 10_000));

        assertTrue(reservation.enforced());
        assertEquals(3, reservation.reservedSteps());
        assertEquals(8_000, reservation.reservedTokens());
        assertFalse(reservation.userStepKey().contains("user@example.com"));
        assertFalse(reservation.sessionStepKey().contains("session-1"));
        assertTrue(reservation.userStepKey().contains(RedisAgentQuotaService.HASH_TAG));
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redis, times(2)).execute(any(RedisScript.class), keys.capture(), args.capture());
        assertEquals(5, keys.getAllValues().get(0).size());
        assertTrue(keys.getAllValues().get(0).stream()
                .allMatch(key -> key.contains(RedisAgentQuotaService.HASH_TAG)));
        assertEquals(List.of("3", "8000", "1", "120"), List.of(args.getAllValues().get(1)));
    }

    @Test
    void shouldFailClosedForExceededQuotaOrRedisFailure() {
        StringRedisTemplate exceeded = mock(StringRedisTemplate.class);
        when(exceeded.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-2L);
        assertThrows(ClientException.class,
                () -> service(exceeded).reserve("u-1", "s-1", 3, 8_000));

        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        when(unavailable.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("connection refused"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service(unavailable).reserve("u-1", "s-1", 3, 8_000));
        assertTrue(failure.getMessage().contains("已拒绝"));
        assertFalse(failure.getMessage().contains("connection refused"));
    }

    @Test
    void shouldRejectInvalidReservationInsteadOfTouchingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisAgentQuotaService service = service(redis);
        assertThrows(IllegalArgumentException.class, () -> service.reserve("", "s-1", 3, 8_000));
        assertThrows(IllegalArgumentException.class, () -> service.reserve("u-1", "s-1", 4, 8_000));
        org.mockito.Mockito.verifyNoInteractions(redis);
    }

    private RedisAgentQuotaService service(StringRedisTemplate redis) {
        AgentQuotaProperties properties = new AgentQuotaProperties();
        return new RedisAgentQuotaService(redis, properties,
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC));
    }
}
