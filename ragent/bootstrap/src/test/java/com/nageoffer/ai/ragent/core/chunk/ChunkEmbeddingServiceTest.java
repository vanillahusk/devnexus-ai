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

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingClient;
import com.nageoffer.ai.ragent.infra.model.ModelSelector;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkEmbeddingServiceTest {

    @Test
    void shouldReuseEmbeddingForSameModelAndContentHash() {
        ModelSelector selector = mock(ModelSelector.class);
        EmbeddingClient client = mock(EmbeddingClient.class);
        AIModelProperties.ModelCandidate candidate = mock(AIModelProperties.ModelCandidate.class);
        when(candidate.getProvider()).thenReturn("test-provider");
        ModelTarget target = new ModelTarget("embedding-v1", candidate, null);
        when(selector.selectEmbeddingCandidates()).thenReturn(List.of(target));
        when(client.provider()).thenReturn("test-provider");
        when(client.embedBatch(List.of("相同正文"), target))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        ChunkEmbeddingService service = new ChunkEmbeddingService(
                selector, List.of(client), new EmbeddingVectorCache(10),
                new ChunkEmbeddingMetrics(new SimpleMeterRegistry()));

        VectorChunk first = chunk("相同正文", "hash-1");
        VectorChunk repeated = chunk("相同正文", "hash-1");
        service.embed(List.of(first), "embedding-v1");
        service.embed(List.of(repeated), "embedding-v1");

        verify(client, times(1)).embedBatch(List.of("相同正文"), target);
        assertArrayEquals(first.getEmbedding(), repeated.getEmbedding());
    }

    private VectorChunk chunk(String content, String hash) {
        return VectorChunk.builder().content(content)
                .metadata(new HashMap<>(Map.of("contentHash", hash))).build();
    }
}
