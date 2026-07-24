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

import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.token.HeuristicTokenCounterService;
import com.nageoffer.ai.ragent.rag.core.prompt.CitationValidator;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.core.prompt.TrustedAnswerPromptService;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedAnswerGeneratorTest {
    @Test
    void shouldReturnOnlyAnswerWithAllowedCitation() {
        LLMService llm = mock(LLMService.class);
        when(llm.chat(any(ChatRequest.class))).thenReturn("使用 processing 队列恢复。[ref:c1]");
        TrustedAnswerGenerator generator = generator(llm);

        TrustedAnswerGenerator.GeneratedAnswer result = generator.generate("怎么恢复", retrieval());

        assertTrue(result.generated());
        assertTrue(result.modelCalled());
        assertEquals(List.of("c1"), result.citations().stream()
                .map(TrustedRetrievalResult.Citation::chunkId).toList());
    }

    @Test
    void shouldRefuseWhenModelUsesCitationOutsideCurrentCandidates() {
        LLMService llm = mock(LLMService.class);
        when(llm.chat(any(ChatRequest.class))).thenReturn("伪造结论。[ref:other]");

        TrustedAnswerGenerator.GeneratedAnswer result = generator(llm).generate("怎么恢复", retrieval());

        assertFalse(result.generated());
        assertTrue(result.modelCalled());
        assertEquals("当前资料不足，无法可靠回答。", result.answer());
        assertTrue(result.citations().isEmpty());
    }

    @Test
    void shouldNotCountModelCallWhenEvidenceIsInsufficient() {
        LLMService llm = mock(LLMService.class);
        TrustedRetrievalResult insufficient = new TrustedRetrievalResult(false, "INSUFFICIENT", Map.of(),
                false, 0, "", List.of());

        TrustedAnswerGenerator.GeneratedAnswer result = generator(llm).generate("未知问题", insufficient);

        assertFalse(result.generated());
        assertFalse(result.modelCalled());
        org.mockito.Mockito.verifyNoInteractions(llm);
    }

    private TrustedAnswerGenerator generator(LLMService llm) {
        CitationValidator validator = new CitationValidator();
        TrustedAnswerPromptService prompt = new TrustedAnswerPromptService(
                new PromptTemplateLoader(new DefaultResourceLoader()), validator);
        return new TrustedAnswerGenerator(prompt, llm, new HeuristicTokenCounterService());
    }

    private TrustedRetrievalResult retrieval() {
        var citation = new TrustedRetrievalResult.Citation("c1", "1001", "8", "Redis", "恢复",
                "processing", 0.1F, null);
        return new TrustedRetrievalResult(true, "LEXICAL_EVIDENCE", Map.of(), false, 20,
                "<untrusted_documents>processing</untrusted_documents>", List.of(citation));
    }
}
