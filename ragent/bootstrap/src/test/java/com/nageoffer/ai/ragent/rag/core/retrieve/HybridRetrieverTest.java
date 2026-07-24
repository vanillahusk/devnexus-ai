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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import org.junit.jupiter.api.Test;
import com.nageoffer.ai.ragent.rag.observability.RagMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrieverTest {
    @Test
    void shouldDegradeToKeywordWhenDenseChannelFails() {
        RetrieverService failedVector = new RetrieverService() {
            @Override
            public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
                throw new IllegalStateException("embedding unavailable");
            }

            @Override
            public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
                throw new IllegalStateException("embedding unavailable");
            }
        };
        KeywordRetriever keyword = request -> List.of(new RetrievedChunk("keyword", "fallback", 3F));

        List<RetrievedChunk> result = new HybridRetriever(failedVector, keyword, metrics())
                .retrieve(RetrieveRequest.builder().query("Redis").topK(5).build());

        assertEquals(1, result.size());
        assertEquals("keyword", result.get(0).getId());
    }

    @Test
    void shouldReturnEmptyResultInsteadOfPropagatingWhenVectorStoreIsUnavailable() {
        RetrieverService failedVector = new RetrieverService() {
            @Override
            public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
                throw new IllegalStateException("postgres unavailable");
            }

            @Override
            public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
                throw new IllegalStateException("postgres unavailable");
            }
        };
        KeywordRetriever failedKeyword = request -> {
            throw new IllegalStateException("postgres unavailable");
        };

        List<RetrievedChunk> result = new HybridRetriever(failedVector, failedKeyword, metrics())
                .retrieve(RetrieveRequest.builder().query("Redis").topK(5).build());

        assertTrue(result.isEmpty());
    }

    private RagMetrics metrics() {
        return new RagMetrics(new SimpleMeterRegistry());
    }
}
