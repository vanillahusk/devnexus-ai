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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkIdentityNormalizerTest {

    @Test
    void shouldGenerateStableIdAndPreserveChunkMetadata() {
        VectorChunk first = VectorChunk.builder().index(0).content("Redis 幂等")
                .metadata(new HashMap<>(java.util.Map.of("headingPath", "Java > Redis"))).build();
        VectorChunk repeated = VectorChunk.builder().index(0).content("Redis 幂等")
                .metadata(new HashMap<>(java.util.Map.of("headingPath", "Java > Redis"))).build();

        ChunkIdentityNormalizer.normalize("doc-7", List.of(first));
        ChunkIdentityNormalizer.normalize("doc-7", List.of(repeated));

        assertEquals(first.getChunkId(), repeated.getChunkId());
        assertEquals(20, first.getChunkId().length());
        assertEquals("Java > Redis", first.getMetadata().get("headingPath"));
        assertEquals(64, String.valueOf(first.getMetadata().get("contentHash")).length());
    }

    @Test
    void shouldScopeStableIdByDocumentAndContent() {
        VectorChunk docA = VectorChunk.builder().index(0).content("same").build();
        VectorChunk docB = VectorChunk.builder().index(0).content("same").build();
        VectorChunk changed = VectorChunk.builder().index(0).content("changed").build();

        ChunkIdentityNormalizer.normalize("doc-a", List.of(docA));
        ChunkIdentityNormalizer.normalize("doc-b", List.of(docB));
        ChunkIdentityNormalizer.normalize("doc-a", List.of(changed));

        assertNotEquals(docA.getChunkId(), docB.getChunkId());
        assertNotEquals(docA.getChunkId(), changed.getChunkId());
    }

    @Test
    void shouldRejectBlankChunk() {
        assertThrows(IllegalArgumentException.class, () ->
                ChunkIdentityNormalizer.normalize("doc", List.of(VectorChunk.builder().content(" ").build())));
    }
}
