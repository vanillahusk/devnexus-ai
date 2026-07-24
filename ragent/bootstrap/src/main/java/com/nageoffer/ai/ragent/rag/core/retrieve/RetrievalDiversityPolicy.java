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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 保持融合排名的前提下限制单文章占比，并优先覆盖不同文章章节。 */
public final class RetrievalDiversityPolicy {
    private static final int DEFAULT_MAX_CHUNKS_PER_ARTICLE = 2;

    public List<RetrievedChunk> select(List<RetrievedChunk> ranked, int topK) {
        if (ranked == null || ranked.isEmpty()) return List.of();
        int limit = Math.max(1, topK);
        List<RetrievedChunk> unique = new ArrayList<>(new LinkedHashMap<>(ranked.stream()
                .filter(chunk -> chunk != null && chunk.getId() != null)
                .collect(java.util.stream.Collectors.toMap(RetrievedChunk::getId, chunk -> chunk,
                        (left, right) -> left, LinkedHashMap::new))).values());
        List<RetrievedChunk> selected = new ArrayList<>();
        Map<String, Integer> articleCounts = new HashMap<>();
        Set<String> articleHeadings = new HashSet<>();

        // 第一轮优先不同文章/章节。
        for (RetrievedChunk chunk : unique) {
            if (selected.size() >= limit) break;
            String article = articleKey(chunk);
            String heading = headingKey(chunk);
            if (articleCounts.getOrDefault(article, 0) >= DEFAULT_MAX_CHUNKS_PER_ARTICLE) continue;
            if (!articleHeadings.add(article + '|' + heading)) continue;
            selected.add(chunk);
            articleCounts.merge(article, 1, Integer::sum);
        }
        // 第二轮填充同章节的次优结果，但仍遵守单文章上限。
        for (RetrievedChunk chunk : unique) {
            if (selected.size() >= limit) break;
            if (selected.stream().anyMatch(existing -> existing.getId().equals(chunk.getId()))) continue;
            String article = articleKey(chunk);
            if (articleCounts.getOrDefault(article, 0) >= DEFAULT_MAX_CHUNKS_PER_ARTICLE) continue;
            selected.add(chunk);
            articleCounts.merge(article, 1, Integer::sum);
        }
        return List.copyOf(selected);
    }

    private String articleKey(RetrievedChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        if (metadata != null) {
            Object articleId = metadata.get("articleId");
            if (articleId != null) return "article:" + articleId;
            Object documentId = metadata.get("doc_id");
            if (documentId != null) return "doc:" + documentId;
        }
        return "chunk:" + chunk.getId();
    }

    private String headingKey(RetrievedChunk chunk) {
        Object heading = chunk.getMetadata() == null ? null : chunk.getMetadata().get("headingPath");
        return heading == null ? "" : String.valueOf(heading);
    }
}
