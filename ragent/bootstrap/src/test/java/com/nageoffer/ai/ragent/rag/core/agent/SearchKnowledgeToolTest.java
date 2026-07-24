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

package com.nageoffer.ai.ragent.rag.core.agent;

import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchKnowledgeToolTest {
    @Test
    void shouldRejectVisibilityOverrideAndNormalizeEquivalentInput() {
        TrustedRetrievalService retrieval = mock(TrustedRetrievalService.class);
        SearchKnowledgeTool tool = new SearchKnowledgeTool(retrieval);

        assertThrows(IllegalArgumentException.class, () -> tool.execute(
                new SearchKnowledgeTool.Input("Redis", 5, Map.of("status", "OFFLINE"))));
        assertEquals(tool.normalizedSignature(new SearchKnowledgeTool.Input(" Redis   Queue ", 5, Map.of("articleId", "1001"))),
                tool.normalizedSignature(new SearchKnowledgeTool.Input("redis queue", 5, Map.of("articleId", "1001"))));
    }

    @Test
    void shouldReturnBoundedSummaryWithoutCopyingDocumentBody() {
        TrustedRetrievalService retrieval = mock(TrustedRetrievalService.class);
        TrustedRetrievalResult result = new TrustedRetrievalResult(true, "LEXICAL_EVIDENCE", Map.of(),
                false, 40, "very-sensitive-full-document", List.of());
        when(retrieval.retrieve(any())).thenReturn(result);
        SearchKnowledgeTool tool = new SearchKnowledgeTool(retrieval);

        AgentToolResult output = tool.execute(new SearchKnowledgeTool.Input("Redis", 5, Map.of()));

        assertEquals("answerable=true,decision=LEXICAL_EVIDENCE,citations=0", output.summary());
        org.junit.jupiter.api.Assertions.assertFalse(output.summary().contains("sensitive"));
    }
}
