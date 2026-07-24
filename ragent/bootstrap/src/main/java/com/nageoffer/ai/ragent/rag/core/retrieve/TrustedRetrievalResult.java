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

import java.util.List;
import java.util.Map;

/** 可信检索结果：上下文、判定信号和引用在同一次候选集合中生成。 */
public record TrustedRetrievalResult(
        boolean answerable,
        String decisionCode,
        Map<String, Object> decisionSignals,
        boolean rerankApplied,
        boolean cacheHit,
        int contextTokens,
        String context,
        List<Citation> citations) {

    public TrustedRetrievalResult(boolean answerable, String decisionCode, Map<String, Object> decisionSignals,
                                  boolean rerankApplied, int contextTokens, String context,
                                  List<Citation> citations) {
        this(answerable, decisionCode, decisionSignals, rerankApplied, false,
                contextTokens, context, citations);
    }

    public TrustedRetrievalResult asCacheHit() {
        return new TrustedRetrievalResult(answerable, decisionCode, decisionSignals, rerankApplied, true,
                contextTokens, context, citations);
    }

    public TrustedRetrievalResult asCacheMiss() {
        return cacheHit ? new TrustedRetrievalResult(answerable, decisionCode, decisionSignals, rerankApplied, false,
                contextTokens, context, citations) : this;
    }

    public record Citation(
            String chunkId,
            String articleId,
            String articleVersion,
            String title,
            String headingPath,
            String snippet,
            Float retrievalScore,
            Float rerankScore) {
    }
}
