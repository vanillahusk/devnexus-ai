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

package com.nageoffer.ai.ragent.infra.chat;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 从冻结集均匀抽取 8 条可回答问题，只评估 HY3 的证据内生成、关键词和引用。
 * 检索与拒答由独立 60 题评测负责，避免混淆误差来源和消耗免费生成配额。
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class Hy3FrozenGenerationEvaluatorTest {
    private static final int SAMPLE_SIZE = 8;
    private static final Pattern REFERENCE = Pattern.compile("\\[ref:a(\\d+)]");

    @Test
    void shouldEvaluateHy3GroundedGenerationOnFrozenSubset() throws Exception {
        Path articlesPath = requiredPath("rag.eval.articles");
        Path queriesPath = requiredPath("rag.eval.queries");
        Path reportPath = requiredPath("rag.eval.report");
        Map<Long, ArticleFixture> articles = loadArticles(articlesPath).stream()
                .filter(ArticleFixture::searchable)
                .collect(Collectors.toMap(ArticleFixture::id, article -> article));
        List<QueryFixture> answerable = loadQueries(queriesPath).stream()
                .filter(query -> !query.shouldRefuse())
                .toList();
        List<QueryFixture> selected = evenlySelect(answerable, SAMPLE_SIZE);

        OpenRouterChatClient client = chatClient();
        ModelTarget target = chatTarget();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<GenerationCase> cases = new ArrayList<>(selected.size());
        try {
            List<Future<GenerationCase>> futures = new ArrayList<>();
            for (QueryFixture query : selected) {
                futures.add(executor.submit((Callable<GenerationCase>) () ->
                        evaluateCase(client, target, query, articles)));
            }
            for (Future<GenerationCase> future : futures) cases.add(future.get());
        } finally {
            executor.shutdownNow();
        }

        GenerationMetrics metrics = summarize(cases);
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        Files.writeString(reportPath, renderReport(cases, metrics), StandardCharsets.UTF_8);

        assertTrue(metrics.successRate() >= 0.75D,
                () -> "HY3 生成通过率低于75%，详见 " + reportPath.toAbsolutePath());
        assertTrue(metrics.invalidCitationCases() == 0,
                () -> "HY3 产生未知引用，详见 " + reportPath.toAbsolutePath());
        System.out.printf(Locale.ROOT,
                "HY3_GENERATION_METRICS cases=%d success_rate=%.4f keyword_hit_rate=%.4f "
                        + "citation_valid_rate=%.4f p95_ms=%d failures=%d report=%s%n",
                cases.size(), metrics.successRate(), metrics.keywordHitRate(),
                metrics.citationValidRate(), metrics.p95Millis(), metrics.failedCalls(),
                reportPath.toAbsolutePath());
    }

    private GenerationCase evaluateCase(
            OpenRouterChatClient client,
            ModelTarget target,
            QueryFixture query,
            Map<Long, ArticleFixture> articles) {
        Set<String> allowedReferences = query.expectedArticleIds().stream()
                .map(id -> "a" + id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String evidence = query.expectedArticleIds().stream()
                .map(articles::get)
                .filter(java.util.Objects::nonNull)
                .map(article -> "[ref:a" + article.id() + "] " + article.searchText())
                .collect(Collectors.joining("\n"));
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("只能依据给定资料用中文简洁回答。每个事实段必须引用 [ref:a数字]；"
                                + "禁止编造引用；资料不足只输出 INSUFFICIENT_EVIDENCE。"),
                        ChatMessage.user("问题：" + query.question()
                                + "\n<untrusted_documents>\n" + evidence
                                + "\n</untrusted_documents>")))
                .temperature(0.1D)
                .maxTokens(220)
                .thinking(false)
                .build();
        long startedAt = System.nanoTime();
        try {
            String answer = client.chat(request, target);
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            Set<String> actualReferences = references(answer);
            boolean citationValid = !actualReferences.isEmpty()
                    && allowedReferences.containsAll(actualReferences);
            boolean keywordHit = query.answerKeywords().stream()
                    .anyMatch(keyword -> containsIgnoreCase(answer, keyword));
            return new GenerationCase(query.id(), query.question(), allowedReferences,
                    actualReferences, keywordHit, citationValid, elapsedMillis, null);
        } catch (RuntimeException failure) {
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return new GenerationCase(query.id(), query.question(), allowedReferences,
                    Set.of(), false, false, elapsedMillis, failure.getClass().getSimpleName());
        }
    }

    private GenerationMetrics summarize(List<GenerationCase> cases) {
        long keywordHits = cases.stream().filter(GenerationCase::keywordHit).count();
        long citationValid = cases.stream().filter(GenerationCase::citationValid).count();
        long passed = cases.stream().filter(item -> item.keywordHit() && item.citationValid()).count();
        long failedCalls = cases.stream().filter(item -> item.failureType() != null).count();
        long invalidCitations = cases.stream()
                .filter(item -> item.failureType() == null && !item.citationValid())
                .count();
        List<Long> latencies = cases.stream().map(GenerationCase::elapsedMillis).sorted().toList();
        int p95Index = Math.max(0, (int) Math.ceil(latencies.size() * 0.95D) - 1);
        return new GenerationMetrics(
                passed / (double) cases.size(),
                keywordHits / (double) cases.size(),
                citationValid / (double) cases.size(),
                latencies.get(p95Index),
                failedCalls,
                invalidCitations);
    }

    private String renderReport(List<GenerationCase> cases, GenerationMetrics metrics) {
        StringBuilder out = new StringBuilder(4096);
        out.append("# HY3 冻结题集生成质量评测\n\n")
                .append("生成时间：").append(OffsetDateTime.now()).append("\n\n")
                .append("> 本报告从 52 条可回答问题中均匀抽取 8 条，并直接提供标注正确证据，"
                        + "只评估 HY3 的证据内生成。60 题检索、Reranker 和拒答由独立报告负责。\n\n")
                .append("## 边界\n\n")
                .append("- 模型：`tencent/hy3:free`；并发：2；每题最大输出：220 Token\n")
                .append("- 仅允许引用本题标注文章；不记录 Prompt、完整证据或完整模型回答\n")
                .append("- HY3 免费变体将在 2026-07-21 下线，本报告是限时验证证据，不是长期可用性承诺\n")
                .append("- 不启动数据库、中间件、Java 服务或 Docker\n\n")
                .append("## 指标\n\n")
                .append(String.format(Locale.ROOT, "- 关键词命中率：`%.4f`%n", metrics.keywordHitRate()))
                .append(String.format(Locale.ROOT, "- 引用合法率：`%.4f`%n", metrics.citationValidRate()))
                .append(String.format(Locale.ROOT, "- 同时通过率：`%.4f`%n", metrics.successRate()))
                .append("- P95：`").append(metrics.p95Millis()).append(" ms`\n")
                .append("- 调用失败：`").append(metrics.failedCalls()).append("`\n")
                .append("- 未知或缺失引用：`").append(metrics.invalidCitationCases()).append("`\n\n")
                .append("## 用例结果\n\n")
                .append("| ID | 问题 | 允许引用 | 实际引用 | 关键词 | 引用 | 耗时(ms) | 失败类型 |\n")
                .append("| --- | --- | --- | --- | --- | --- | ---: | --- |\n");
        cases.forEach(item -> out.append("| ").append(item.id()).append(" | ")
                .append(item.question().replace("|", "\\|")).append(" | ")
                .append(item.allowedReferences()).append(" | ")
                .append(item.actualReferences()).append(" | ")
                .append(item.keywordHit() ? "通过" : "失败").append(" | ")
                .append(item.citationValid() ? "通过" : "失败").append(" | ")
                .append(item.elapsedMillis()).append(" | ")
                .append(item.failureType() == null ? "" : item.failureType()).append(" |\n"));
        out.append("\n## 结论边界\n\n")
                .append("- 该结果证明所选 8 题的证据内回答与引用行为，不代表 60 题完整生成准确率或生产 P95。\n")
                .append("- 最终回答仍必须经过服务端引用白名单校验；模型输出正确不能替代代码约束。\n");
        return out.toString();
    }

    private OpenRouterChatClient chatClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(55))
                .callTimeout(Duration.ofSeconds(65))
                .build();
        return new OpenRouterChatClient(httpClient, Runnable::run);
    }

    private ModelTarget chatTarget() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(System.getenv().getOrDefault("OPENROUTER_BASE_URL", "https://openrouter.ai"));
        provider.setApiKey(System.getenv("OPENROUTER_API_KEY"));
        provider.setEndpoints(new HashMap<>(Map.of("chat", "/api/v1/chat/completions")));
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("hy3-free");
        candidate.setProvider("openrouter");
        candidate.setModel("tencent/hy3:free");
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private Set<String> references(String answer) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = REFERENCE.matcher(answer == null ? "" : answer);
        while (matcher.find()) result.add("a" + matcher.group(1));
        return result;
    }

    private boolean containsIgnoreCase(String text, String expected) {
        return text != null && expected != null
                && text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private List<QueryFixture> evenlySelect(List<QueryFixture> source, int count) {
        if (source.size() <= count) return source;
        List<QueryFixture> selected = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int sourceIndex = (int) Math.round(index * (source.size() - 1D) / (count - 1D));
            selected.add(source.get(sourceIndex));
        }
        return selected;
    }

    private List<ArticleFixture> loadArticles(Path path) throws IOException {
        return loadTsv(path, 7).stream().map(row -> new ArticleFixture(
                Long.parseLong(row[0]), row[1], row[2], row[3], row[4],
                Boolean.parseBoolean(row[5]), Boolean.parseBoolean(row[6]))).toList();
    }

    private List<QueryFixture> loadQueries(Path path) throws IOException {
        return loadTsv(path, 7).stream().map(row -> new QueryFixture(
                row[0], row[1], parseLongSet(row[2]), parseStringSet(row[5]),
                Boolean.parseBoolean(row[6]))).toList();
    }

    private List<String[]> loadTsv(Path path, int expectedColumns) throws IOException {
        List<String[]> rows = Files.readAllLines(path, StandardCharsets.UTF_8).stream().skip(1)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split("\\t", -1)).collect(Collectors.toList());
        for (String[] row : rows) {
            if (row.length != expectedColumns) {
                throw new IOException("invalid TSV column count in " + path + ": " + Arrays.toString(row));
            }
        }
        return rows;
    }

    private Set<Long> parseLongSet(String raw) {
        return Arrays.stream(raw.split("\\|")).filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> parseStringSet(String raw) {
        return Arrays.stream(raw.split("\\|")).filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing system property: " + property);
        }
        return Path.of(value);
    }

    private record ArticleFixture(
            long id,
            String title,
            String shortTitle,
            String summary,
            String content,
            boolean online,
            boolean deleted) {
        boolean searchable() {
            return online && !deleted;
        }

        String searchText() {
            return String.join("\n", title, shortTitle, summary, content);
        }
    }

    private record QueryFixture(
            String id,
            String question,
            Set<Long> expectedArticleIds,
            Set<String> answerKeywords,
            boolean shouldRefuse) {
    }

    private record GenerationCase(
            String id,
            String question,
            Set<String> allowedReferences,
            Set<String> actualReferences,
            boolean keywordHit,
            boolean citationValid,
            long elapsedMillis,
            String failureType) {
    }

    private record GenerationMetrics(
            double successRate,
            double keywordHitRate,
            double citationValidRate,
            long p95Millis,
            long failedCalls,
            long invalidCitationCases) {
    }
}
