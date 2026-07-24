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

import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelCallMetricsTest {

    @Test
    void shouldExposeOnlyLowCardinalityCapabilityStatusAndReasonTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ModelCallMetrics metrics = new ModelCallMetrics(registry);

        ModelCallMetrics.Call call = metrics.start(ModelCapability.CHAT);
        assertEquals(1D, registry.get("rag.model.calls.inflight").tag("capability", "chat").gauge().value());
        call.close("failure");
        metrics.rejected(ModelCapability.CHAT, "concurrency");
        metrics.retried(ModelCapability.EMBEDDING);

        assertEquals(0D, registry.get("rag.model.calls.inflight").tag("capability", "chat").gauge().value());
        assertEquals(1L, registry.get("rag.model.calls.duration")
                .tags("capability", "chat", "status", "failure").timer().count());
        assertEquals(1D, registry.get("rag.model.calls.rejected")
                .tags("capability", "chat", "reason", "concurrency").counter().count());
        assertEquals(1D, registry.get("rag.model.calls.retried")
                .tag("capability", "embedding").counter().count());
    }
}
