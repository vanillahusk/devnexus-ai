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

package com.nageoffer.ai.ragent.core.chunk.strategy;

import com.nageoffer.ai.ragent.core.chunk.TextBoundaryOptions;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.infra.token.HeuristicTokenCounterService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureAwareTextChunkerTest {
    private final HeuristicTokenCounterService tokenCounter = new HeuristicTokenCounterService();
    private final StructureAwareTextChunker chunker = new StructureAwareTextChunker(tokenCounter);

    @Test
    void shouldReturnNoChunkForBlankDocument() {
        assertTrue(chunker.chunk(" \n ", new TextBoundaryOptions(100, 10, 120, 20)).isEmpty());
    }

    @Test
    void shouldKeepHeadingPathAndMetadataAfterFinalRenumbering() {
        String markdown = "# Java\n\n引言。\n\n## Redis\n\nRedis正文。";

        List<VectorChunk> chunks = chunker.chunk(markdown,
                new TextBoundaryOptions(12, 0, 20, 4));

        assertFalse(chunks.isEmpty());
        VectorChunk redis = chunks.stream()
                .filter(chunk -> chunk.getContent().contains("Redis正文"))
                .findFirst().orElseThrow();
        assertEquals("Java > Redis", redis.getMetadata().get("headingPath"));
        assertEquals(redis.getContent().length(), redis.getMetadata().get("charCount"));
        assertEquals(64, String.valueOf(redis.getMetadata().get("contentHash")).length());
    }

    @Test
    void shouldRecursivelySplitOversizedPlainParagraph() {
        String paragraph = "第一句很长需要切分。第二句继续提供内容。第三句继续提供内容。第四句继续提供内容。";

        List<VectorChunk> chunks = chunker.chunk(paragraph,
                new TextBoundaryOptions(16, 0, 24, 6));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getContent().length() <= 24));
        assertEquals(paragraph, chunks.stream().map(VectorChunk::getContent)
                .reduce("", String::concat));
    }

    @Test
    void shouldKeepFencedCodeBlockIntact() {
        String markdown = "# 示例\n\n```java\nclass Demo {\n  void run() {}\n}\n```\n\n结束。";

        List<VectorChunk> chunks = chunker.chunk(markdown,
                new TextBoundaryOptions(20, 0, 28, 5));

        long codeFenceCount = chunks.stream()
                .filter(chunk -> chunk.getContent().contains("```java"))
                .count();
        assertEquals(1, codeFenceCount);
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getContent().contains("void run() {}")));
    }

    @Test
    void shouldPropagateFrontMatterWithoutIndexingItAsContent() {
        String markdown = "---\nsourceType: ARTICLE\narticleId: 12\narticleVersion: 4\n"
                + "status: ONLINE\ntitle: RocketMQ: 可靠消息\n---\n# 标题\n\n正文";

        List<VectorChunk> chunks = chunker.chunk(markdown,
                new TextBoundaryOptions(100, 0, 120, 10));

        assertEquals(12L, chunks.get(0).getMetadata().get("articleId"));
        assertEquals(4L, chunks.get(0).getMetadata().get("articleVersion"));
        assertEquals("ARTICLE", chunks.get(0).getMetadata().get("sourceType"));
        assertEquals("ONLINE", chunks.get(0).getMetadata().get("status"));
        assertEquals("RocketMQ: 可靠消息", chunks.get(0).getMetadata().get("title"));
        assertFalse(chunks.get(0).getContent().contains("articleVersion:"));
    }

    @Test
    void shouldEnforceTokenBudgetAndTokenOverlapForMixedText() {
        String unit = "Redis分布式锁需要校验owner并使用Lua原子释放。Spring transaction retry boundary. ";
        String markdown = "# 并发控制\n\n" + unit.repeat(180);
        TextBoundaryOptions options = new TextBoundaryOptions(
                4000, 0, 6000, 1000,
                650, 75, 800, 500);

        List<VectorChunk> chunks = chunker.chunk(markdown, options);

        assertTrue(chunks.size() > 2);
        for (VectorChunk chunk : chunks) {
            int actual = tokenCounter.countTokens(chunk.getContent());
            assertEquals(actual, chunk.getMetadata().get("tokenCount"));
            assertTrue(actual <= 800, "ordinary chunk exceeds token max: " + actual);
        }
        assertTrue(chunks.subList(0, chunks.size() - 1).stream()
                .allMatch(chunk -> tokenCounter.countTokens(chunk.getContent()) >= 500));
        int appliedOverlap = (Integer) chunks.get(1).getMetadata().get("overlapTokenCount");
        assertTrue(appliedOverlap >= 50 && appliedOverlap <= 75,
                "unexpected overlap tokens: " + appliedOverlap);
    }
}
