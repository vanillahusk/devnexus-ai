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

import java.util.List;

/** 工具结果只暴露有限摘要、Token 估算和引用，不记录完整敏感正文。 */
public record AgentToolResult(String summary, int estimatedTokens,
                              List<TrustedRetrievalResult.Citation> citations,
                              TrustedRetrievalResult retrieval) {
    public AgentToolResult {
        summary = summary == null ? "" : summary.substring(0, Math.min(500, summary.length()));
        estimatedTokens = Math.max(0, estimatedTokens);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
