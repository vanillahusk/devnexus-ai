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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** Chunk Embedding 数量、缓存结果、批次失败与耗时指标。 */
@Component
public class ChunkEmbeddingMetrics {

    private final MeterRegistry registry;

    public ChunkEmbeddingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void chunks(String result, int count) {
        if (count <= 0) return;
        Counter.builder("rag.embedding.chunks")
                .tag("result", safeResult(result))
                .register(registry)
                .increment(count);
    }

    public Timer.Sample startBatch() {
        return Timer.start(registry);
    }

    public void finishBatch(Timer.Sample sample, String status) {
        sample.stop(Timer.builder("rag.embedding.batch.duration")
                .tag("status", "success".equals(status) ? "success" : "failure")
                .publishPercentileHistogram()
                .register(registry));
        Counter.builder("rag.embedding.batches")
                .tag("status", "success".equals(status) ? "success" : "failure")
                .register(registry)
                .increment();
    }

    private String safeResult(String result) {
        return switch (result) {
            case "cached", "computed", "preexisting" -> result;
            default -> "other";
        };
    }
}
