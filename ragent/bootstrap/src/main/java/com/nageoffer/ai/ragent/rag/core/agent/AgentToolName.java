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

import java.util.Arrays;

/** 第一版 Agent 唯一允许的四个只读工具。 */
public enum AgentToolName {
    SEARCH_KNOWLEDGE("searchKnowledge", true),
    GET_ARTICLE_DETAIL("getArticleDetail", false),
    SEARCH_RELATED_ARTICLES("searchRelatedArticles", true),
    GET_CONVERSATION_SUMMARY("getConversationSummary", false);

    private final String value;
    private final boolean retrieval;

    AgentToolName(String value, boolean retrieval) {
        this.value = value;
        this.retrieval = retrieval;
    }

    public String value() { return value; }
    public boolean retrieval() { return retrieval; }

    public static AgentToolName fromValue(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Agent工具不在白名单: " + value));
    }
}
