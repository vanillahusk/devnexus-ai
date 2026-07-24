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

package com.nageoffer.ai.ragent.rag.core.retrieve.cache;

import com.nageoffer.ai.ragent.rag.core.agent.usage.AgentCostProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiGovernanceConfigurationPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AgentCostProperties.class, RetrievalCacheProperties.class);

    @Test
    void shouldRegisterAndBindCostAndCacheProperties() {
        runner.withPropertyValues(
                "rag.agent.cost.model-name=test-model",
                "rag.agent.cost.estimated-micros-per-million-tokens=250000",
                "rag.retrieval.cache.key-prefix=rag:test:cache",
                "rag.retrieval.cache.ttl-seconds=30",
                "rag.retrieval.cache.mutation-guard-ttl-seconds=60")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    AgentCostProperties cost = context.getBean(AgentCostProperties.class);
                    RetrievalCacheProperties cache = context.getBean(RetrievalCacheProperties.class);
                    assertEquals("test-model", cost.getModelName());
                    assertEquals(250_000, cost.getEstimatedMicrosPerMillionTokens());
                    assertEquals("rag:test:cache", cache.getKeyPrefix());
                    assertEquals(30, cache.getTtlSeconds());
                });
    }

    @Test
    void shouldRejectMutationGuardShorterThanCacheTtl() {
        runner.withPropertyValues(
                "rag.retrieval.cache.ttl-seconds=60",
                "rag.retrieval.cache.mutation-guard-ttl-seconds=30")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
