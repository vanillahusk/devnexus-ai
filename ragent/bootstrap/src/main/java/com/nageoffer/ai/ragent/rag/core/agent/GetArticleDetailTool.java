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

import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.apache.skywalking.apm.toolkit.trace.Trace;

import java.util.List;

/**
 * 按文章 ID 读取当前 ONLINE 事实快照。正文仍包装为不可信资料，输出引用与上下文来自同一快照。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.agent.enabled", havingValue = "true")
public class GetArticleDetailTool implements AgentTool<GetArticleDetailTool.Input> {
    private static final int MAX_CONTEXT_CHARS = 20_000;
    private final PaicodingArticleClient articleClient;
    private final TokenCounterService tokenCounterService;

    @Override public AgentToolName name() { return AgentToolName.GET_ARTICLE_DETAIL; }
    @Override public Class<Input> inputType() { return Input.class; }

    @Override
    public String normalizedSignature(Input input) {
        validate(input);
        return Long.toString(input.articleId());
    }

    @Override
    @Trace(operationName = "rag.agent.tool.get_article_detail")
    public AgentToolResult execute(Input input) {
        validate(input);
        return articleClient.findOnlineArticle(input.articleId()).map(this::toResult)
                .orElseGet(() -> new AgentToolResult("visible=false,citations=0", 1, List.of(), null));
    }

    private AgentToolResult toResult(PaicodingArticleClient.ArticleSnapshot article) {
        String chunkId = "article-detail:" + article.articleId() + ":v" + article.articleVersion();
        String body = ("title=" + escapeUntrusted(article.title()) + "\nsummary=" + escapeUntrusted(article.summary())
                + "\ncontent=" + escapeUntrusted(article.content()));
        if (body.length() > MAX_CONTEXT_CHARS) body = body.substring(0, MAX_CONTEXT_CHARS);
        String context = "<untrusted_documents>\n<document ref=\"" + chunkId + "\">\n"
                + body + "\n</document>\n</untrusted_documents>";
        Integer counted = tokenCounterService.countTokens(context);
        int tokens = counted == null ? Math.max(1, context.length() / 4) : Math.max(1, counted);
        String snippet = safe(article.summary());
        if (snippet.length() > 300) snippet = snippet.substring(0, 300);
        TrustedRetrievalResult.Citation citation = new TrustedRetrievalResult.Citation(chunkId,
                Long.toString(article.articleId()), Long.toString(article.articleVersion()), safe(article.title()),
                "文章详情", snippet, null, null);
        TrustedRetrievalResult retrieval = new TrustedRetrievalResult(true, "FACT_SOURCE_DETAIL",
                java.util.Map.of("articleId", article.articleId(), "articleVersion", article.articleVersion()),
                false, tokens, context, List.of(citation));
        return new AgentToolResult("visible=true,articleId=" + article.articleId() + ",articleVersion="
                + article.articleVersion() + ",citations=1", tokens, List.of(citation), retrieval);
    }

    private void validate(Input input) {
        if (input == null || input.articleId() <= 0) throw new IllegalArgumentException("getArticleDetail.articleId必须为正数");
    }

    private String safe(String value) { return value == null ? "" : value; }

    private String escapeUntrusted(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record Input(long articleId) {}
}
