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
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复合证据判定。RRF 分数只负责排序，不作为固定拒答阈值；判定同时考虑词项覆盖、
 * 精确标识符、Rerank 相关度/差值、有效 Chunk 数和引用完整性。
 */
@Component
public class EvidenceDecisionPolicy {
    private static final Pattern EXACT_IDENTIFIER = Pattern.compile(
            "(?i)(?:[a-z_$][a-z0-9_.$:-]{2,}|\\b[0-9]{3,}\\b)");

    public Decision decide(String query, List<RetrievedChunk> chunks, boolean rerankApplied) {
        Map<String, Object> signals = new LinkedHashMap<>();
        if (chunks == null || chunks.isEmpty()) return new Decision(false, "NO_EVIDENCE", signals);

        Set<String> queryTerms = meaningfulTerms(query);
        double coverage = chunks.stream().mapToDouble(chunk -> coverage(queryTerms, chunk.getText())).max().orElse(0D);
        Set<String> identifiers = identifiers(query);
        boolean exactIdentifierHit = !identifiers.isEmpty() && chunks.stream()
                .anyMatch(chunk -> containsAllIgnoreCase(chunk.getText(), identifiers));
        long completeCitations = chunks.stream().filter(this::hasCompleteCitation).count();
        float topScore = score(chunks.get(0));
        float secondScore = chunks.size() > 1 ? score(chunks.get(1)) : 0F;
        float scoreGap = topScore - secondScore;
        long strongRerankChunks = rerankApplied
                ? chunks.stream().filter(chunk -> score(chunk) >= 0.20F).count() : 0L;

        signals.put("termCoverage", round(coverage));
        signals.put("exactIdentifierHit", exactIdentifierHit);
        signals.put("effectiveChunkCount", chunks.size());
        signals.put("completeCitationCount", completeCitations);
        signals.put("rerankApplied", rerankApplied);
        if (rerankApplied) {
            signals.put("topRerankScore", topScore);
            signals.put("topScoreGap", scoreGap);
            signals.put("strongRerankChunkCount", strongRerankChunks);
        }

        if (completeCitations == 0) return new Decision(false, "CITATION_INCOMPLETE", signals);
        if (exactIdentifierHit) return new Decision(true, "EXACT_IDENTIFIER_EVIDENCE", signals);
        if (rerankApplied && topScore >= 0.35F && coverage >= 0.15D
                && (scoreGap >= 0.03F || strongRerankChunks >= 2)) {
            return new Decision(true, "RERANK_EVIDENCE", signals);
        }
        if (!rerankApplied && coverage >= 0.50D) {
            return new Decision(true, "LEXICAL_EVIDENCE", signals);
        }
        return new Decision(false, "INSUFFICIENT_EVIDENCE", signals);
    }

    private Set<String> meaningfulTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        for (String term : Bm25Scorer.tokenize(query == null ? "" : query)) {
            if (term.codePointCount(0, term.length()) >= 2 || term.chars().allMatch(ch -> ch < 128)) terms.add(term);
        }
        return terms;
    }

    private double coverage(Set<String> terms, String text) {
        if (terms.isEmpty() || text == null) return 0D;
        String normalized = text.toLowerCase(Locale.ROOT);
        long hits = terms.stream().filter(normalized::contains).count();
        return hits / (double) terms.size();
    }

    private Set<String> identifiers(String query) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = EXACT_IDENTIFIER.matcher(query == null ? "" : query);
        while (matcher.find()) result.add(matcher.group().toLowerCase(Locale.ROOT));
        return result;
    }

    private boolean containsAllIgnoreCase(String text, Set<String> terms) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return terms.stream().allMatch(normalized::contains);
    }

    private boolean hasCompleteCitation(RetrievedChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        return metadata != null && present(metadata.get("articleId"))
                && present(metadata.get("title")) && present(metadata.get("headingPath"));
    }

    private boolean present(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private float score(RetrievedChunk chunk) {
        return chunk.getScore() == null ? 0F : chunk.getScore();
    }

    private double round(double value) {
        return Math.round(value * 10_000D) / 10_000D;
    }

    public record Decision(boolean answerable, String code, Map<String, Object> signals) {
    }
}
