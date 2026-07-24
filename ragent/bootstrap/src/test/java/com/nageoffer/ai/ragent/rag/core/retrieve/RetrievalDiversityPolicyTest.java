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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalDiversityPolicyTest {
    @Test
    void shouldLimitOneArticleAndPreferDifferentHeadings() {
        List<RetrievedChunk> selected = new RetrievalDiversityPolicy().select(List.of(
                chunk("a-1", 1, "Java > Redis"),
                chunk("a-2", 1, "Java > Redis"),
                chunk("a-3", 1, "Java > RocketMQ"),
                chunk("b-1", 2, "MySQL > Index"),
                chunk("c-1", 3, "Spring > Transaction")), 5);

        long articleOne = selected.stream()
                .filter(chunk -> Integer.valueOf(1).equals(chunk.getMetadata().get("articleId"))).count();
        assertEquals(2, articleOne);
        assertTrue(selected.stream().anyMatch(chunk -> "b-1".equals(chunk.getId())));
        assertTrue(selected.stream().anyMatch(chunk -> "c-1".equals(chunk.getId())));
        assertTrue(selected.stream().noneMatch(chunk -> "a-2".equals(chunk.getId())));
    }

    @Test
    void shouldDeduplicateChunkIdsBeforeSelection() {
        RetrievedChunk duplicate = chunk("same", 1, "Redis");
        assertEquals(1, new RetrievalDiversityPolicy().select(List.of(duplicate, duplicate), 5).size());
    }

    private RetrievedChunk chunk(String id, int articleId, String heading) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("articleId", articleId);
        metadata.put("headingPath", heading);
        return RetrievedChunk.builder().id(id).text(id).score(1F).metadata(metadata).build();
    }
}
