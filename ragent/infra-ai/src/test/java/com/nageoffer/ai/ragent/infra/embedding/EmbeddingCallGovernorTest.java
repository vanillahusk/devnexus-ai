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

package com.nageoffer.ai.ragent.infra.embedding;

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.http.ModelClientErrorType;
import com.nageoffer.ai.ragent.infra.http.ModelClientException;
import com.nageoffer.ai.ragent.infra.model.ModelCallMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingCallGovernorTest {
    @Test
    void shouldRetryTransientFailureOnce() {
        AIModelProperties properties = properties(10, 2, 1L);
        EmbeddingCallGovernor governor = governor(properties);
        AtomicInteger calls = new AtomicInteger();

        String result = governor.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new ModelClientException("temporary", ModelClientErrorType.SERVER_ERROR, 503);
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void shouldNotRetryClientError() {
        EmbeddingCallGovernor governor = governor(properties(10, 3, 1L));
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ModelClientException.class, () -> governor.execute(() -> {
            calls.incrementAndGet();
            throw new ModelClientException("bad request", ModelClientErrorType.CLIENT_ERROR, 400);
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void shouldRejectWhenLocalRateBudgetIsExhausted() {
        EmbeddingCallGovernor governor = governor(properties(1, 1, 1L));
        assertEquals("first", governor.execute(() -> "first"));
        assertThrows(ModelClientException.class, () -> governor.execute(() -> "second"));
    }

    private AIModelProperties properties(int requestsPerSecond, int attempts, long backoffMs) {
        AIModelProperties properties = new AIModelProperties();
        properties.getEmbeddingGovernance().setRequestsPerSecond(requestsPerSecond);
        properties.getEmbeddingGovernance().setMaxAttempts(attempts);
        properties.getEmbeddingGovernance().setRetryBackoffMs(backoffMs);
        return properties;
    }

    private EmbeddingCallGovernor governor(AIModelProperties properties) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new EmbeddingCallGovernor(properties, new ModelCallMetrics(registry));
    }
}
