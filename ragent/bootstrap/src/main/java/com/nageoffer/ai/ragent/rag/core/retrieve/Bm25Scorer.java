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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 轻量 BM25：英文/标识符按词，连续中文同时生成单字与二元词。 */
public final class Bm25Scorer {
    private static final Pattern TERM_RUN = Pattern.compile("[a-zA-Z0-9_.$:-]+|[\\p{IsHan}]+");
    private static final double K1 = 1.2D;
    private static final double B = 0.75D;

    public List<RetrievedChunk> score(String query, List<RetrievedChunk> candidates, int topK) {
        if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) return List.of();
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return List.of();
        List<List<String>> documents = candidates.stream().map(chunk -> tokenize(chunk.getText())).toList();
        double averageLength = documents.stream().mapToInt(List::size).average().orElse(1D);
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> document : documents) {
            for (String term : new HashSet<>(document)) documentFrequency.merge(term, 1, Integer::sum);
        }
        List<RetrievedChunk> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            List<String> document = documents.get(i);
            Map<String, Integer> termFrequency = new HashMap<>();
            document.forEach(term -> termFrequency.merge(term, 1, Integer::sum));
            double score = 0D;
            for (String term : queryTerms) {
                int tf = termFrequency.getOrDefault(term, 0);
                if (tf == 0) continue;
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1D + (candidates.size() - df + 0.5D) / (df + 0.5D));
                double denominator = tf + K1 * (1D - B + B * document.size() / Math.max(1D, averageLength));
                score += idf * tf * (K1 + 1D) / denominator;
            }
            if (score > 0D) {
                RetrievedChunk source = candidates.get(i);
                scored.add(RetrievedChunk.builder().id(source.getId()).text(source.getText())
                        .metadata(source.getMetadata()).score((float) score).build());
            }
        }
        return scored.stream().sorted(Comparator.comparing(RetrievedChunk::getScore).reversed())
                .limit(Math.max(1, topK)).toList();
    }

    static List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        Matcher matcher = TERM_RUN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String run = matcher.group();
            if (run.codePoints().allMatch(Bm25Scorer::isHan)) {
                List<String> chars = run.codePoints().mapToObj(cp -> new String(Character.toChars(cp))).toList();
                terms.addAll(chars);
                for (int i = 0; i + 1 < chars.size(); i++) terms.add(chars.get(i) + chars.get(i + 1));
            } else {
                terms.add(run);
            }
        }
        return terms;
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
