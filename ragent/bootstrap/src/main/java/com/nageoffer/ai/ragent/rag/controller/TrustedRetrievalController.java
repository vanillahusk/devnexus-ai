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

package com.nageoffer.ai.ragent.rag.controller;

import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalService;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrieveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 内部可信检索入口；只返回本次候选集合产生的引用。 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class TrustedRetrievalController {
    private final TrustedRetrievalService trustedRetrievalService;

    @PostMapping("/rag/retrieve/trusted")
    public Result<TrustedRetrievalResult> retrieve(@RequestBody TrustedRetrieveRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new ClientException("检索问题不能为空");
        }
        if (request.getTopK() < 1 || request.getTopK() > 8) throw new ClientException("topK必须在1到8之间");
        if (request.getCandidateTopK() < request.getTopK() || request.getCandidateTopK() > 50) {
            throw new ClientException("candidateTopK必须不小于topK且不大于50");
        }
        if (request.getMaxContextTokens() < 256 || request.getMaxContextTokens() > 12000) {
            throw new ClientException("maxContextTokens必须在256到12000之间");
        }
        return Results.success(trustedRetrievalService.retrieve(request));
    }
}
