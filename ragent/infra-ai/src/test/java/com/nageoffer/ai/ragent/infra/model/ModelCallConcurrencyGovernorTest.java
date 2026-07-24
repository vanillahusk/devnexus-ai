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

package com.nageoffer.ai.ragent.infra.model;

import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelCallConcurrencyGovernorTest {

    @Test
    void shouldRejectSecondChatCallWhenBulkheadIsFullAndRecoverAfterRelease() {
        AIModelProperties properties = new AIModelProperties();
        properties.getModelCallGovernance().setChatMaxConcurrent(1);
        properties.getModelCallGovernance().setAcquireTimeoutMs(1L);
        ModelCallConcurrencyGovernor governor = governor(properties);

        ModelCallConcurrencyGovernor.Lease lease = governor.acquire(ModelCapability.CHAT);
        assertThrows(RemoteException.class, () -> governor.acquire(ModelCapability.CHAT));
        lease.close();

        assertEquals("ok", governor.execute(ModelCapability.CHAT, () -> "ok"));
    }

    @Test
    void shouldUseIndependentChatAndRerankPermits() {
        AIModelProperties properties = new AIModelProperties();
        properties.getModelCallGovernance().setChatMaxConcurrent(1);
        properties.getModelCallGovernance().setRerankMaxConcurrent(1);
        ModelCallConcurrencyGovernor governor = governor(properties);

        try (ModelCallConcurrencyGovernor.Lease ignored = governor.acquire(ModelCapability.CHAT)) {
            assertEquals(3, governor.execute(ModelCapability.RERANK, () -> 3));
        }
    }

    private ModelCallConcurrencyGovernor governor(AIModelProperties properties) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new ModelCallConcurrencyGovernor(properties, new ModelCallMetrics(registry));
    }
}
