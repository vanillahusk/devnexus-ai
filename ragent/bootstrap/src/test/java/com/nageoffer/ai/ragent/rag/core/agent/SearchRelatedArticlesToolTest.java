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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchRelatedArticlesToolTest {
    @Test
    void shouldReturnOnlyBoundedArticleCountSummaryAndCitations() {
        TrustedRetrievalService retrieval = mock(TrustedRetrievalService.class);
        List<TrustedRetrievalResult.Citation> citations = List.of(citation("c1", "1001"), citation("c2", "1001"),
                citation("c3", "1002"));
        when(retrieval.retrieve(any())).thenReturn(new TrustedRetrievalResult(true, "LEXICAL_EVIDENCE", Map.of(),
                false, 80, "full-document-must-stay-untrusted", citations));
        SearchRelatedArticlesTool tool = new SearchRelatedArticlesTool(retrieval);

        AgentToolResult result = tool.execute(new SearchRelatedArticlesTool.Input("RocketMQ", null, null));

        assertEquals("relatedArticles=2,decision=LEXICAL_EVIDENCE", result.summary());
        assertEquals(3, result.citations().size());
    }

    private TrustedRetrievalResult.Citation citation(String chunk, String article) {
        return new TrustedRetrievalResult.Citation(chunk, article, "1", "title", "heading", "text", 0.1F, null);
    }
}
