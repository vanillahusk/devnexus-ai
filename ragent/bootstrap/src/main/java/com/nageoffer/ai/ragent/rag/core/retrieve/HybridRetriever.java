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
import com.nageoffer.ai.ragent.rag.observability.RagMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/** Dense + BM25 双通道；任一通道异常时保留另一通道的可用结果。 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class HybridRetriever {
    private final RetrieverService vectorRetriever;
    private final KeywordRetriever keywordRetriever;
    private final RagMetrics metrics;
    private final ReciprocalRankFusion fusion = new ReciprocalRankFusion();
    private final RetrievalDiversityPolicy diversityPolicy = new RetrievalDiversityPolicy();

    public HybridRetriever(RetrieverService vectorRetriever, KeywordRetriever keywordRetriever, RagMetrics metrics) {
        this.vectorRetriever = vectorRetriever;
        this.keywordRetriever = keywordRetriever;
        this.metrics = metrics;
    }

    @Trace(operationName = "rag.retrieval.hybrid")
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        int candidateTopK = Math.min(100, Math.max(20, Math.max(1, request.getTopK()) * 4));
        RetrieveRequest candidateRequest = RetrieveRequest.builder()
                .query(request.getQuery()).topK(candidateTopK)
                .collectionName(request.getCollectionName()).metadataFilters(request.getMetadataFilters()).build();
        List<RetrievedChunk> dense = safeRetrieve("dense", () -> vectorRetriever.retrieve(candidateRequest));
        List<RetrievedChunk> keyword = safeRetrieve("keyword", () -> keywordRetriever.retrieve(candidateRequest));
        List<RetrievedChunk> fused = metrics.stage("fusion",
                () -> fusion.fuse(List.of(keyword, dense), candidateTopK));
        return diversityPolicy.select(fused, request.getTopK());
    }

    private List<RetrievedChunk> safeRetrieve(String channel, RetrievalCall call) {
        try {
            List<RetrievedChunk> result = metrics.stage(channel, call::execute);
            return result == null ? List.of() : result;
        } catch (RuntimeException failure) {
            log.warn("retrieval channel degraded, channel={}, cause={}", channel,
                    failure.getClass().getSimpleName());
            metrics.degraded(channel);
            return List.of();
        }
    }

    @FunctionalInterface
    private interface RetrievalCall {
        List<RetrievedChunk> execute();
    }
}
