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
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.retrieve.cache.TrustedRetrievalCache;
import com.nageoffer.ai.ragent.rag.observability.RagMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class TrustedRetrievalServiceTest {
    @Test
    void shouldBypassRetrievalAndRerankWhenVersionedCacheHits() {
        HybridRetriever hybrid = mock(HybridRetriever.class);
        RerankService rerank = mock(RerankService.class);
        TrustedRetrievalCache cache = mock(TrustedRetrievalCache.class);
        TrustedRetrievalResult cached = new TrustedRetrievalResult(true, "OK", Map.of(), true, true,
                20, "cached", List.of());
        when(cache.lookup(org.mockito.ArgumentMatchers.any())).thenReturn(
                new TrustedRetrievalCache.Lookup("key", 7, true, Optional.of(cached)));
        TrustedRetrievalService service = new TrustedRetrievalService(hybrid, rerank,
                new ContextBudgetPolicy(text -> 30), new EvidenceDecisionPolicy(), cache, metrics());

        TrustedRetrievalResult result = service.retrieve(TrustedRetrieveRequest.builder().query("Redis").build());

        assertTrue(result.cacheHit());
        verifyNoInteractions(hybrid, rerank);
    }

    @Test
    void shouldForceVisibilityFiltersPreserveScoresAndBuildCitationsFromSelectedCandidates() {
        AtomicReference<RetrieveRequest> captured = new AtomicReference<>();
        RetrieverService vector = emptyVector();
        KeywordRetriever keyword = request -> {
            captured.set(request);
            return List.of(candidate());
        };
        HybridRetriever hybrid = new HybridRetriever(vector, keyword, metrics());
        RerankService rerank = (query, candidates, topN) -> candidates.stream().map(source -> {
            Map<String, Object> metadata = new LinkedHashMap<>(source.getMetadata());
            return RetrievedChunk.builder().id(source.getId()).text(source.getText()).score(0.91F)
                    .metadata(metadata).build();
        }).toList();
        TrustedRetrievalCache cache = mock(TrustedRetrievalCache.class);
        when(cache.lookup(org.mockito.ArgumentMatchers.any())).thenReturn(
                new TrustedRetrievalCache.Lookup("", 0, false, Optional.empty()));
        TrustedRetrievalService service = new TrustedRetrievalService(hybrid, rerank,
                new ContextBudgetPolicy(text -> 30), new EvidenceDecisionPolicy(), cache, metrics());

        TrustedRetrievalResult result = service.retrieve(TrustedRetrieveRequest.builder()
                .query("PROCESSING状态").candidateTopK(20).topK(6).maxContextTokens(4000)
                .metadataFilters(Map.of("status", "OFFLINE", "articleId", "1003")).build());

        assertEquals("ARTICLE", captured.get().getMetadataFilters().get("sourceType"));
        assertEquals("ONLINE", captured.get().getMetadataFilters().get("status"));
        assertTrue(result.answerable());
        assertTrue(result.rerankApplied());
        assertEquals(30, result.contextTokens());
        assertTrue(result.context().startsWith("<untrusted_documents>"));
        assertEquals("1003", result.citations().get(0).articleId());
        assertEquals(0.016393442F, result.citations().get(0).retrievalScore(), 0.000001F);
        assertEquals(0.91F, result.citations().get(0).rerankScore());
    }

    private RagMetrics metrics() {
        return new RagMetrics(new SimpleMeterRegistry());
    }

    private RetrieverService emptyVector() {
        return new RetrieverService() {
            @Override
            public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
                return List.of();
            }

            @Override
            public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
                return List.of();
            }
        };
    }

    private RetrievedChunk candidate() {
        return RetrievedChunk.builder().id("chunk-1003").text("Redis任务进入PROCESSING后由恢复任务接管")
                .score(3F).metadata(Map.of("articleId", "1003", "articleVersion", "4",
                        "title", "Redis可靠队列", "headingPath", "恢复", "tokenCount", 30)).build();
    }
}
