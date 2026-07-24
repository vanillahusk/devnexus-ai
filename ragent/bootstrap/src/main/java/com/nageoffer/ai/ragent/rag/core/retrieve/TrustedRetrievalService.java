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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hybrid → Rerank → Token预算 → 证据判定 → 引用 的单一可信检索编排。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustedRetrievalService {
    private final HybridRetriever hybridRetriever;
    private final RerankService rerankService;
    private final ContextBudgetPolicy contextBudgetPolicy;
    private final EvidenceDecisionPolicy evidenceDecisionPolicy;
    private final TrustedRetrievalCache retrievalCache;
    private final RagMetrics metrics;

    @Trace(operationName = "rag.retrieval.trusted")
    public TrustedRetrievalResult retrieve(TrustedRetrieveRequest request) {
        TrustedRetrievalCache.Lookup lookup = retrievalCache.lookup(request);
        if (lookup.result().isPresent()) {
            TrustedRetrievalResult cached = lookup.result().get();
            metrics.trustedResult(cached);
            return cached;
        }
        TrustedRetrievalResult result = metrics.stage("trusted", () -> retrieveCurrent(request));
        retrievalCache.put(lookup, result);
        metrics.trustedResult(result);
        return result;
    }

    private TrustedRetrievalResult retrieveCurrent(TrustedRetrieveRequest request) {
        int candidateTopK = Math.max(request.getTopK(), request.getCandidateTopK());
        Map<String, Object> filters = new LinkedHashMap<>();
        if (request.getMetadataFilters() != null) filters.putAll(request.getMetadataFilters());
        filters.put("sourceType", "ARTICLE");
        filters.put("status", "ONLINE");

        List<RetrievedChunk> candidates = hybridRetriever.retrieve(RetrieveRequest.builder()
                .query(request.getQuery()).topK(candidateTopK).collectionName(request.getCollectionName())
                .metadataFilters(filters).build()).stream().map(this::withRetrievalScore).toList();
        RerankOutcome rerank = rerank(request.getQuery(), candidates, request.getTopK());
        ContextBudgetPolicy.Selection selection = contextBudgetPolicy.select(
                rerank.chunks(), request.getTopK(), request.getMaxContextTokens());
        EvidenceDecisionPolicy.Decision decision = evidenceDecisionPolicy.decide(
                request.getQuery(), selection.chunks(), rerank.applied());
        List<TrustedRetrievalResult.Citation> citations = selection.chunks().stream().map(this::citation).toList();
        String context = decision.answerable() ? formatUntrustedContext(selection.chunks()) : "";
        return new TrustedRetrievalResult(decision.answerable(), decision.code(), decision.signals(),
                rerank.applied(), selection.tokenCount(), context, citations);
    }

    private RerankOutcome rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (candidates.isEmpty()) return new RerankOutcome(List.of(), false);
        try {
            List<RetrievedChunk> reranked = metrics.stage("rerank",
                    () -> rerankService.rerank(query, candidates, topK));
            if (reranked == null || reranked.isEmpty()) return new RerankOutcome(limit(candidates, topK), false);
            boolean applied = reranked.stream().anyMatch(this::scoreChangedByRerank);
            return new RerankOutcome(limit(reranked, topK), applied);
        } catch (RuntimeException failure) {
            log.warn("rerank degraded to fused order, cause={}", failure.getClass().getSimpleName());
            metrics.degraded("rerank");
            return new RerankOutcome(limit(candidates, topK), false);
        }
    }

    private RetrievedChunk withRetrievalScore(RetrievedChunk source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.getMetadata() != null) metadata.putAll(source.getMetadata());
        metadata.put("retrievalScore", source.getScore() == null ? 0F : source.getScore());
        return RetrievedChunk.builder().id(source.getId()).text(source.getText()).score(source.getScore())
                .metadata(metadata).build();
    }

    private boolean scoreChangedByRerank(RetrievedChunk chunk) {
        Object retrieval = chunk.getMetadata() == null ? null : chunk.getMetadata().get("retrievalScore");
        if (!(retrieval instanceof Number number) || chunk.getScore() == null) return false;
        return Math.abs(chunk.getScore() - number.floatValue()) > 0.000001F;
    }

    private List<RetrievedChunk> limit(List<RetrievedChunk> chunks, int topK) {
        return new ArrayList<>(chunks.subList(0, Math.min(Math.max(1, topK), chunks.size())));
    }

    private TrustedRetrievalResult.Citation citation(RetrievedChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata() == null ? Map.of() : chunk.getMetadata();
        Object retrievalScore = metadata.get("retrievalScore");
        return new TrustedRetrievalResult.Citation(chunk.getId(), value(metadata, "articleId"),
                value(metadata, "articleVersion"), value(metadata, "title"), value(metadata, "headingPath"),
                snippet(chunk.getText()), retrievalScore instanceof Number number ? number.floatValue() : null,
                scoreChangedByRerank(chunk) ? chunk.getScore() : null);
    }

    private String formatUntrustedContext(List<RetrievedChunk> chunks) {
        StringBuilder out = new StringBuilder("<untrusted_documents>\n");
        for (RetrievedChunk chunk : chunks) {
            Map<String, Object> metadata = chunk.getMetadata() == null ? Map.of() : chunk.getMetadata();
            out.append("[ref=").append(chunk.getId()).append(" articleId=").append(value(metadata, "articleId"))
                    .append(" title=").append(safeAttribute(value(metadata, "title")))
                    .append(" heading=").append(safeAttribute(value(metadata, "headingPath"))).append("]\n")
                    .append(chunk.getText() == null ? "" : chunk.getText()).append("\n[/ref]\n");
        }
        return out.append("</untrusted_documents>").toString();
    }

    private String value(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    private String safeAttribute(String value) {
        return value.replace("\n", " ").replace("\r", " ").replace("]", "）");
    }

    private String snippet(String text) {
        if (text == null) return "";
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "…";
    }

    private record RerankOutcome(List<RetrievedChunk> chunks, boolean applied) {
    }
}
