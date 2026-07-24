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
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.rag.core.prompt.CitationValidator;
import com.nageoffer.ai.ragent.rag.core.prompt.TrustedAnswerPromptService;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 普通可信 RAG 的同步收口：生成失败或引用越界时固定拒答，不返回未校验文本。 */
@Service
@RequiredArgsConstructor
public class TrustedAnswerGenerator {
    private static final String SAFE_REFUSAL = "当前资料不足，无法可靠回答。";
    private final TrustedAnswerPromptService promptService;
    private final LLMService llmService;
    private final TokenCounterService tokenCounterService;

    public GeneratedAnswer generate(String question, TrustedRetrievalResult retrieval) {
        TrustedAnswerPromptService.AnswerPlan plan = promptService.prepare(question, retrieval);
        if (!plan.shouldGenerate()) {
            return new GeneratedAnswer(plan.fallbackAnswer(), false, false,
                    "EVIDENCE_INSUFFICIENT", 1, List.of());
        }
        try {
            String answer = llmService.chat(ChatRequest.builder().messages(plan.messages()).temperature(0D).topP(1D)
                    .maxTokens(1_200).thinking(false).enableTools(false).build());
            CitationValidator.Validation validation = promptService.validateAnswer(answer, plan);
            if (!validation.valid()) {
                return new GeneratedAnswer(SAFE_REFUSAL, false, true,
                        validation.code(), estimatedTokens(answer), List.of());
            }
            Set<String> referenced = validation.referencedChunkIds();
            List<TrustedRetrievalResult.Citation> used = plan.allowedCitations().stream()
                    .filter(citation -> referenced.contains(citation.chunkId())).toList();
            return new GeneratedAnswer(answer, true, true, "OK", estimatedTokens(answer), used);
        } catch (RuntimeException failure) {
            return new GeneratedAnswer(SAFE_REFUSAL, false, true, "GENERATION_FAILED", 1, List.of());
        }
    }

    private int estimatedTokens(String answer) {
        if (answer == null || answer.isBlank()) return 1;
        Integer count = tokenCounterService.countTokens(answer);
        return count == null ? Math.max(1, answer.length() / 4) : Math.max(1, count);
    }

    public record GeneratedAnswer(String answer, boolean generated, boolean modelCalled,
                                  String code, int estimatedTokens,
                                  List<TrustedRetrievalResult.Citation> citations) {}
}
