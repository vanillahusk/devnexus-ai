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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceDecisionPolicyTest {
    private final EvidenceDecisionPolicy policy = new EvidenceDecisionPolicy();

    @Test
    void shouldAcceptExactJavaIdentifierWithCompleteCitation() {
        EvidenceDecisionPolicy.Decision decision = policy.decide("PROCESSING状态有什么作用",
                List.of(chunk("Redis任务进入PROCESSING后由恢复任务接管", 0.1F, true)), false);

        assertTrue(decision.answerable());
        assertEquals("EXACT_IDENTIFIER_EVIDENCE", decision.code());
    }

    @Test
    void shouldRefuseWeakUnrelatedEvidenceInsteadOfUsingRrfScoreThreshold() {
        EvidenceDecisionPolicy.Decision decision = policy.decide("今天上海天气怎么样",
                List.of(chunk("RocketMQ消息积压恢复", 99F, true)), false);

        assertFalse(decision.answerable());
        assertEquals("INSUFFICIENT_EVIDENCE", decision.code());
    }

    @Test
    void shouldRefuseWhenCitationCannotBeTraced() {
        EvidenceDecisionPolicy.Decision decision = policy.decide("Redis可靠队列",
                List.of(chunk("Redis可靠队列使用pending和processing", 0.9F, false)), true);

        assertFalse(decision.answerable());
        assertEquals("CITATION_INCOMPLETE", decision.code());
    }

    private RetrievedChunk chunk(String text, float score, boolean completeCitation) {
        Map<String, Object> metadata = completeCitation
                ? Map.of("articleId", "1003", "title", "Redis可靠队列", "headingPath", "恢复")
                : Map.of("articleId", "1003");
        return RetrievedChunk.builder().id("chunk-1").text(text).score(score).metadata(metadata).build();
    }
}
