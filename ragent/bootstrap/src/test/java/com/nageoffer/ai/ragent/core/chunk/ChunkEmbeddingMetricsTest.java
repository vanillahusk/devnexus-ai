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

package com.nageoffer.ai.ragent.core.chunk;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkEmbeddingMetricsTest {

    @Test
    void shouldRecordBoundedChunkAndBatchResults() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChunkEmbeddingMetrics metrics = new ChunkEmbeddingMetrics(registry);
        metrics.chunks("cached", 3);
        metrics.chunks("computed", 2);
        Timer.Sample sample = metrics.startBatch();
        metrics.finishBatch(sample, "failure");

        assertEquals(3D, registry.get("rag.embedding.chunks").tag("result", "cached").counter().count());
        assertEquals(2D, registry.get("rag.embedding.chunks").tag("result", "computed").counter().count());
        assertEquals(1D, registry.get("rag.embedding.batches").tag("status", "failure").counter().count());
        assertEquals(1L, registry.get("rag.embedding.batch.duration").tag("status", "failure").timer().count());
    }
}
