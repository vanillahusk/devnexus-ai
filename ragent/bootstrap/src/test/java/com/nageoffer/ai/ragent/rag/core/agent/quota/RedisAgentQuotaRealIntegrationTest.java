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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅在显式开关开启时连接本机 Redis；使用随机身份隔离 Key，并在 finally 中精准清理。 */
@EnabledIfEnvironmentVariable(named = "RAGENT_REAL_REDIS_QUOTA", matches = "true")
class RedisAgentQuotaRealIntegrationTest {

    @Test
    void shouldAtomicallyReserveSettleAndRejectQuotaOverflow() {
        String redisHost = System.getenv().getOrDefault("RAGENT_REDIS_HOST", "127.0.0.1");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("RAGENT_REDIS_PORT", "6379"));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisHost, redisPort);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        AgentQuotaProperties properties = new AgentQuotaProperties();
        properties.setUserDailySteps(3);
        properties.setUserDailyTokens(8_000);
        properties.setSessionDailySteps(3);
        properties.setSessionDailyTokens(8_000);
        RedisAgentQuotaService service = new RedisAgentQuotaService(redis, properties);
        String nonce = UUID.randomUUID().toString();
        AgentQuotaService.Reservation reservation = null;
        try {
            reservation = service.reserve("quota-test-user-" + nonce, "quota-test-session-" + nonce, 3, 8_000);
            List<String> quotaKeys = List.of(reservation.userStepKey(), reservation.userTokenKey(),
                    reservation.sessionStepKey(), reservation.sessionTokenKey());
            assertEquals(List.of("3", "8000", "3", "8000"), redis.opsForValue().multiGet(quotaKeys));
            assertTrue(quotaKeys.stream().allMatch(key -> Boolean.TRUE.equals(redis.getExpire(key) > 0)));

            AgentExecutionBudget.Usage actual = new AgentExecutionBudget.Usage(1, 1, 120, 1_000);
            service.settle(reservation, actual);
            assertEquals(List.of("1", "120", "1", "120"), redis.opsForValue().multiGet(quotaKeys));
            assertEquals("SETTLED", redis.opsForValue().get(reservation.reservationKey()));

            service.settle(reservation, new AgentExecutionBudget.Usage(0, 0, 0, 1_000));
            assertEquals(List.of("1", "120", "1", "120"), redis.opsForValue().multiGet(quotaKeys));
            assertThrows(ClientException.class,
                    () -> service.reserve("quota-test-user-" + nonce, "quota-test-session-" + nonce, 3, 8_000));

            Long deleted = redis.delete(List.of(reservation.userStepKey(), reservation.userTokenKey(),
                    reservation.sessionStepKey(), reservation.sessionTokenKey(), reservation.reservationKey()));
            assertEquals(5L, deleted);
            reservation = null;
        } finally {
            if (reservation != null) {
                redis.delete(List.of(reservation.userStepKey(), reservation.userTokenKey(),
                        reservation.sessionStepKey(), reservation.sessionTokenKey(), reservation.reservationKey()));
            }
            connectionFactory.destroy();
        }
    }
}
