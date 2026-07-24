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
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.embedding.SiliconFlowEmbeddingClient;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import com.nageoffer.ai.ragent.infra.rerank.SiliconFlowRerankClient;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 Qwen3 Embedding/Reranker 在 PaiCoding 冻结题集上评估 Dense、
 * BM25 + Dense RRF 与精排。默认构建不会访问外网，只有显式提供密钥时才运行。
 */
@EnabledIfEnvironmentVariable(named = "SILICONFLOW_API_KEY", matches = ".+")
class Qwen3DenseHybridOfflineEvaluatorTest {
    private static final String MODEL = "Qwen/Qwen3-Embedding-8B";
    private static final String RERANK_MODEL = "Qwen/Qwen3-Reranker-8B";
    private static final int DIMENSION = 1536;
    private static final float RERANK_REFUSAL_THRESHOLD = 0.35F;
    private static final double LEGACY_RECALL_AT_5 = 0.5160D;
    private static final double LEGACY_MRR = 0.5288D;
    private static final double BM25_RECALL_AT_5 = 0.9744D;
    private static final double BM25_MRR = 0.8990D;

    @Test
    void shouldEvaluateRealDenseHybridAndRerankerAgainstFrozenDataset() throws IOException {
        Path articlesPath = requiredPath("rag.eval.articles");
        Path queriesPath = requiredPath("rag.eval.queries");
        Path reportPath = requiredPath("rag.eval.report");
        String profile = System.getProperty("rag.eval.profile", "legacy");
        boolean expandedProfile = "expanded".equalsIgnoreCase(profile);
        List<ArticleFixture> articles = loadArticles(articlesPath);
        List<QueryFixture> queries = loadQueries(queriesPath);
        List<ArticleFixture> searchable = articles.stream().filter(ArticleFixture::searchable).toList();

        assertTrue(articles.size() >= (expandedProfile ? 140 : 25), "评测语料规模不足");
        assertTrue(queries.size() >= (expandedProfile ? 140 : 50), "评测问题规模不足");
        assertEquals(queries.size(), queries.stream().map(QueryFixture::id).distinct().count(), "问题ID必须唯一");

        long startedAt = System.nanoTime();
        List<String> embeddingInputs = new ArrayList<>(searchable.size() + queries.size());
        searchable.stream().map(ArticleFixture::searchText).forEach(embeddingInputs::add);
        queries.stream().map(QueryFixture::question).forEach(embeddingInputs::add);
        List<List<Float>> vectors = embeddingClient().embedBatch(embeddingInputs, embeddingTarget());
        long embeddingElapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        List<List<Float>> articleVectors = vectors.subList(0, searchable.size());
        List<List<Float>> queryVectors = vectors.subList(searchable.size(), vectors.size());
        long rerankStartedAt = System.nanoTime();
        EvaluationResult result = evaluate(
                articles, searchable, queries, articleVectors, queryVectors, !expandedProfile);
        long rerankElapsedMillis = Duration.ofNanos(System.nanoTime() - rerankStartedAt).toMillis();
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        Files.writeString(reportPath,
                renderReport(result, articles.size(), searchable.size(), queries.size(),
                        embeddingElapsedMillis, rerankElapsedMillis, profile),
                StandardCharsets.UTF_8);

        if (!expandedProfile) {
            assertTrue(result.dense().recallAt5() >= LEGACY_RECALL_AT_5,
                    () -> "Dense Recall@5低于旧基线，详见 " + reportPath.toAbsolutePath());
            assertTrue(result.hybrid().recallAt5() >= BM25_RECALL_AT_5,
                    () -> "Hybrid Recall@5低于同题集BM25，详见 " + reportPath.toAbsolutePath());
            assertTrue(result.hybrid().mrr() >= BM25_MRR,
                    () -> "Hybrid MRR低于同题集BM25，详见 " + reportPath.toAbsolutePath());
            assertTrue(result.reranked().recallAt5() >= LEGACY_RECALL_AT_5,
                    () -> "Reranker Recall@5低于旧基线，详见 " + reportPath.toAbsolutePath());
        } else {
            assertMetricRange(result.dense());
            assertMetricRange(result.hybrid());
            assertMetricRange(result.reranked());
        }
        System.out.printf(Locale.ROOT,
                "QWEN3_RAG_METRICS cases=%d dense_recall_at_5=%.4f dense_mrr=%.4f "
                        + "hybrid_recall_at_5=%.4f hybrid_mrr=%.4f "
                        + "rerank_recall_at_5=%.4f rerank_mrr=%.4f "
                        + "threshold_refusal_accuracy=%.4f composite_refusal_accuracy=%.4f "
                        + "embedding_ms=%d rerank_ms=%d report=%s%n",
                queries.size(), result.dense().recallAt5(), result.dense().mrr(),
                result.hybrid().recallAt5(), result.hybrid().mrr(),
                result.reranked().recallAt5(), result.reranked().mrr(),
                result.thresholdDecision().refusalAccuracy(),
                result.compositeDecision().refusalAccuracy(), embeddingElapsedMillis,
                rerankElapsedMillis, reportPath.toAbsolutePath());
    }

    private EvaluationResult evaluate(
            List<ArticleFixture> allArticles,
            List<ArticleFixture> searchable,
            List<QueryFixture> queries,
            List<List<Float>> articleVectors,
            List<List<Float>> queryVectors,
            boolean applyCurrentArticlePrior) {
        List<RetrievedChunk> corpus = searchable.stream()
                .map(article -> chunk(article, 0F))
                .toList();
        Bm25Scorer bm25Scorer = new Bm25Scorer();
        ReciprocalRankFusion fusion = new ReciprocalRankFusion();
        SiliconFlowRerankClient rerankClient = rerankClient();
        ModelTarget rerankTarget = rerankTarget();
        EvidenceDecisionPolicy evidencePolicy = new EvidenceDecisionPolicy();
        List<ChannelCase> denseCases = new ArrayList<>(queries.size());
        List<ChannelCase> hybridCases = new ArrayList<>(queries.size());
        List<ChannelCase> rerankedCases = new ArrayList<>(queries.size());
        List<Boolean> compositeAnswers = new ArrayList<>(queries.size());

        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            QueryFixture query = queries.get(queryIndex);
            List<RetrievedChunk> dense = denseRank(searchable, articleVectors, queryVectors.get(queryIndex));
            List<RetrievedChunk> keyword = bm25Scorer.score(query.question(), corpus, searchable.size());
            List<RetrievedChunk> hybrid = fusion.fuse(List.of(keyword, dense), searchable.size());
            List<RetrievedChunk> reranked = rerankClient.rerank(
                    query.question(), hybrid.stream().limit(20).toList(), 10, rerankTarget);
            List<RetrievedChunk> prioritizedReranked = applyCurrentArticlePrior
                    ? prioritizeCurrentChunks(reranked, query, allArticles)
                    : reranked;
            denseCases.add(evaluateCase(query,
                    applyCurrentArticlePrior
                            ? prioritizeCurrentArticle(dense, query, allArticles)
                            : dense.stream().map(chunk -> Long.parseLong(chunk.getId())).toList(),
                    Float.NaN));
            hybridCases.add(evaluateCase(query,
                    applyCurrentArticlePrior
                            ? prioritizeCurrentArticle(hybrid, query, allArticles)
                            : hybrid.stream().map(chunk -> Long.parseLong(chunk.getId())).toList(),
                    Float.NaN));
            rerankedCases.add(evaluateCase(query,
                    prioritizedReranked.stream().map(chunk -> Long.parseLong(chunk.getId())).toList(),
                    prioritizedReranked.isEmpty() ? Float.NaN : prioritizedReranked.get(0).getScore()));
            compositeAnswers.add(evidencePolicy.decide(
                    query.question(), prioritizedReranked.stream().limit(6).toList(), true).answerable());
        }
        return new EvaluationResult(
                summarize(denseCases),
                summarize(hybridCases),
                summarize(rerankedCases),
                thresholdDecisionMetrics(rerankedCases),
                decisionMetrics(rerankedCases, compositeAnswers),
                denseCases,
                hybridCases,
                rerankedCases);
    }

    private List<RetrievedChunk> denseRank(
            List<ArticleFixture> articles,
            List<List<Float>> articleVectors,
            List<Float> queryVector) {
        List<RetrievedChunk> ranked = new ArrayList<>(articles.size());
        for (int index = 0; index < articles.size(); index++) {
            ranked.add(chunk(articles.get(index), (float) cosine(queryVector, articleVectors.get(index))));
        }
        ranked.sort(Comparator.comparing(RetrievedChunk::getScore).reversed());
        return ranked;
    }

    private RetrievedChunk chunk(ArticleFixture article, float score) {
        return new RetrievedChunk(Long.toString(article.id()), article.searchText(), score,
                Map.of(
                        "articleId", Long.toString(article.id()),
                        "title", article.title(),
                        "headingPath", article.title()));
    }

    private List<Long> prioritizeCurrentArticle(
            List<RetrievedChunk> rankedChunks,
            QueryFixture query,
            List<ArticleFixture> articles) {
        List<Long> ranked = new ArrayList<>(rankedChunks.stream()
                .map(chunk -> Long.parseLong(chunk.getId())).toList());
        if (query.currentArticleId() != null
                && articles.stream().anyMatch(article -> article.searchable() && article.id() == query.currentArticleId())) {
            ranked.remove(query.currentArticleId());
            ranked.add(0, query.currentArticleId());
        }
        return ranked;
    }

    private List<RetrievedChunk> prioritizeCurrentChunks(
            List<RetrievedChunk> rankedChunks,
            QueryFixture query,
            List<ArticleFixture> articles) {
        List<RetrievedChunk> ranked = new ArrayList<>(rankedChunks);
        if (query.currentArticleId() == null
                || articles.stream().noneMatch(article ->
                article.searchable() && article.id() == query.currentArticleId())) {
            return ranked;
        }
        String currentId = Long.toString(query.currentArticleId());
        RetrievedChunk current = ranked.stream()
                .filter(chunk -> currentId.equals(chunk.getId()))
                .findFirst()
                .orElse(null);
        if (current != null) {
            ranked.remove(current);
            ranked.add(0, current);
        }
        return ranked;
    }

    private ChannelCase evaluateCase(QueryFixture query, List<Long> ranked, float topScore) {
        if (query.shouldRefuse()) {
            return new ChannelCase(query.id(), query.question(), query.category(),
                    query.expectedArticleIds(), ranked,
                    true, 0D, 0D, 0D, false, topScore);
        }
        double recall = intersectionSize(ranked.stream().limit(5).toList(), query.expectedArticleIds())
                / (double) query.expectedArticleIds().size();
        double mrr = reciprocalRank(ranked, query.expectedArticleIds());
        double ndcg = ndcgAt(ranked, query.expectedArticleIds(), 10);
        boolean citationHit = ranked.stream().limit(6).anyMatch(query.expectedArticleIds()::contains);
        return new ChannelCase(query.id(), query.question(), query.category(),
                query.expectedArticleIds(), ranked,
                false, recall, mrr, ndcg, citationHit, topScore);
    }

    private ChannelMetrics summarize(List<ChannelCase> cases) {
        List<ChannelCase> answerable = cases.stream().filter(item -> !item.shouldRefuse()).toList();
        List<ChannelCase> refusal = cases.stream().filter(ChannelCase::shouldRefuse).toList();
        return new ChannelMetrics(
                answerable.stream().mapToDouble(ChannelCase::recallAt5).average().orElse(0D),
                answerable.stream().mapToDouble(ChannelCase::reciprocalRank).average().orElse(0D),
                answerable.stream().mapToDouble(ChannelCase::ndcgAt10).average().orElse(0D),
                answerable.stream().filter(ChannelCase::citationHit).count() / (double) answerable.size(),
                refusal.stream().filter(item -> !item.rankedIds().isEmpty()).count() / (double) refusal.size());
    }

    private DecisionMetrics thresholdDecisionMetrics(List<ChannelCase> rerankedCases) {
        List<Boolean> answers = rerankedCases.stream()
                .map(item -> item.topScore() >= RERANK_REFUSAL_THRESHOLD)
                .toList();
        return decisionMetrics(rerankedCases, answers);
    }

    private DecisionMetrics decisionMetrics(
            List<ChannelCase> rerankedCases,
            List<Boolean> predictedAnswers) {
        List<ChannelCase> answerable = rerankedCases.stream().filter(item -> !item.shouldRefuse()).toList();
        List<ChannelCase> refusal = rerankedCases.stream().filter(ChannelCase::shouldRefuse).toList();
        long acceptedAnswerable = 0;
        long correctlyRefused = 0;
        for (int index = 0; index < rerankedCases.size(); index++) {
            ChannelCase item = rerankedCases.get(index);
            boolean predictedAnswer = predictedAnswers.get(index);
            if (!item.shouldRefuse() && predictedAnswer) acceptedAnswerable++;
            if (item.shouldRefuse() && !predictedAnswer) correctlyRefused++;
        }
        return new DecisionMetrics(
                acceptedAnswerable / (double) answerable.size(),
                correctlyRefused / (double) refusal.size(),
                (acceptedAnswerable + correctlyRefused) / (double) rerankedCases.size());
    }

    private SiliconFlowEmbeddingClient embeddingClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(90))
                .callTimeout(Duration.ofSeconds(120))
                .build();
        return new SiliconFlowEmbeddingClient(httpClient);
    }

    private SiliconFlowRerankClient rerankClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(45))
                .callTimeout(Duration.ofSeconds(60))
                .build();
        return new SiliconFlowRerankClient(httpClient);
    }

    private ModelTarget embeddingTarget() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(System.getenv().getOrDefault("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn"));
        provider.setApiKey(System.getenv("SILICONFLOW_API_KEY"));
        provider.setEndpoints(new HashMap<>(Map.of("embedding", "/v1/embeddings")));
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("qwen-emb-8b");
        candidate.setProvider("siliconflow");
        candidate.setModel(MODEL);
        candidate.setDimension(DIMENSION);
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private ModelTarget rerankTarget() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(System.getenv().getOrDefault("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn"));
        provider.setApiKey(System.getenv("SILICONFLOW_API_KEY"));
        provider.setEndpoints(new HashMap<>(Map.of("rerank", "/v1/rerank")));
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("qwen3-rerank-siliconflow");
        candidate.setProvider("siliconflow");
        candidate.setModel(RERANK_MODEL);
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private double cosine(List<Float> left, List<Float> right) {
        assertEquals(left.size(), right.size(), "向量维度必须一致");
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<ArticleFixture> loadArticles(Path path) throws IOException {
        return loadTsv(path, 7).stream().map(row -> new ArticleFixture(Long.parseLong(row[0]), row[1], row[2],
                row[3], row[4], Boolean.parseBoolean(row[5]), Boolean.parseBoolean(row[6]))).toList();
    }

    private List<QueryFixture> loadQueries(Path path) throws IOException {
        return loadTsv(path, 7).stream().map(row -> new QueryFixture(row[0], row[1], parseLongSet(row[2]),
                row[3].isBlank() ? null : Long.parseLong(row[3]),
                row[4].isBlank() ? "UNCLASSIFIED" : row[4], Boolean.parseBoolean(row[6]))).toList();
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
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split("\\|")).map(Long::parseLong)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int intersectionSize(List<Long> ranked, Set<Long> relevant) {
        return (int) ranked.stream().filter(relevant::contains).count();
    }

    private double reciprocalRank(List<Long> ranked, Set<Long> relevant) {
        for (int index = 0; index < ranked.size(); index++) {
            if (relevant.contains(ranked.get(index))) return 1D / (index + 1D);
        }
        return 0D;
    }

    private double ndcgAt(List<Long> ranked, Set<Long> relevant, int k) {
        double dcg = 0D;
        for (int index = 0; index < Math.min(k, ranked.size()); index++) {
            if (relevant.contains(ranked.get(index))) dcg += 1D / log2(index + 2D);
        }
        double ideal = 0D;
        for (int index = 0; index < Math.min(k, relevant.size()); index++) {
            ideal += 1D / log2(index + 2D);
        }
        return ideal == 0D ? 0D : dcg / ideal;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }

    private Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing system property: " + property);
        }
        return Path.of(value);
    }

    private String renderReport(
            EvaluationResult result,
            int articleCount,
            int searchableCount,
            int queryCount,
            long embeddingElapsedMillis,
            long rerankElapsedMillis,
            String profile) {
        boolean expandedProfile = "expanded".equalsIgnoreCase(profile);
        long refusalCount = result.rerankedCases().stream().filter(ChannelCase::shouldRefuse).count();
        int embeddingBatches = (searchableCount + queryCount + 31) / 32;
        StringBuilder out = new StringBuilder(8192);
        out.append(expandedProfile
                        ? "# Qwen3 扩大规模困难集检索评测报告\n\n"
                        : "# Qwen3 真实 Dense、Hybrid 与 Reranker 离线评测报告\n\n")
                .append("生成时间：").append(OffsetDateTime.now()).append("\n\n")
                .append("> 本报告使用真实 ").append(MODEL).append(" 与 ").append(RERANK_MODEL)
                .append(expandedProfile
                        ? "，评估独立的合成困难集。未调用生成模型；结果不能与旧60题直接作同题同比。\n\n"
                        : "，复用旧 RAG 冻结题集和统一指标口径。未调用生成模型。\n\n")
                .append("## 数据与运行边界\n\n")
                .append("- Profile：`").append(profile).append("`\n")
                .append("- 文章：").append(articleCount).append(" 篇；可检索文章：")
                .append(searchableCount).append(" 篇；问题：").append(queryCount).append(" 条\n")
                .append("- 可回答问题：").append(queryCount - refusalCount)
                .append(" 条；应拒答问题：").append(refusalCount).append(" 条\n")
                .append("- 模型：`").append(MODEL).append("`；向量维度：").append(DIMENSION).append("\n")
                .append("- Embedding 输入：").append(searchableCount + queryCount)
                .append(" 条；客户端最多 32 条/批，因此本次为 ").append(embeddingBatches)
                .append(" 个远程批次\n")
                .append("- Embedding 总耗时：").append(embeddingElapsedMillis).append(" ms\n")
                .append("- Reranker：每题 Hybrid Top20 → Top10，共 ").append(queryCount)
                .append(" 个串行请求；总耗时：").append(rerankElapsedMillis).append(" ms\n")
                .append("- 不启动数据库、Redis、RocketMQ、Java 服务或 Docker\n")
                .append("- 离线评测将每篇文章合并为一个候选文档；生产环境实际按结构化 Chunk 检索\n")
                .append("- Query 与 Document 均使用原文直接向量化，保持当前生产实现口径，未额外加入任务指令\n\n")
                .append(expandedProfile
                        ? "- 困难集不应用currentArticleId置顶；近似文章、历史方案和容量文档均参与真实竞争\n"
                        : "- legacy profile保留两条当前文章上下文先验；该先验不计入扩大规模困难集\n")
                .append("## 指标\n\n")
                .append(expandedProfile
                        ? "| 指标 | 真实 Dense | Hybrid | Hybrid + Reranker |\n"
                          + "| --- | ---: | ---: | ---: |\n"
                        : "| 指标 | 旧 MySQL LIKE | BM25 | 真实 Dense | Hybrid | Hybrid + Reranker |\n"
                          + "| --- | ---: | ---: | ---: | ---: | ---: |\n");
        if (expandedProfile) {
            out.append(expandedMetricRow("Recall@5", result.dense().recallAt5(),
                            result.hybrid().recallAt5(), result.reranked().recallAt5()))
                    .append(expandedMetricRow("MRR", result.dense().mrr(),
                            result.hybrid().mrr(), result.reranked().mrr()))
                    .append(expandedMetricRow("nDCG@10", result.dense().ndcgAt10(),
                            result.hybrid().ndcgAt10(), result.reranked().ndcgAt10()))
                    .append(expandedMetricRow("引用命中率", result.dense().citationHitRate(),
                            result.hybrid().citationHitRate(), result.reranked().citationHitRate()));
        } else {
            out.append(metricRow("Recall@5", 0.5160D, BM25_RECALL_AT_5,
                            result.dense().recallAt5(), result.hybrid().recallAt5(),
                            result.reranked().recallAt5()))
                    .append(metricRow("MRR", 0.5288D, BM25_MRR,
                            result.dense().mrr(), result.hybrid().mrr(), result.reranked().mrr()))
                    .append(metricRow("nDCG@10", 0.5175D, 0.9195D,
                            result.dense().ndcgAt10(), result.hybrid().ndcgAt10(),
                            result.reranked().ndcgAt10()))
                    .append(metricRow("引用命中率", 0.5385D, 1D, result.dense().citationHitRate(),
                            result.hybrid().citationHitRate(), result.reranked().citationHitRate()));
        }
        out.append("\n## 分题型结果（扩大规模时用于识别送分题）\n\n")
                .append(categoryMetrics(result))
                .append("\n## Reranker 固定阈值拒答代理\n\n")
                .append("- 阈值：Top1 `relevance_score >= ")
                .append(String.format(Locale.ROOT, "%.2f", RERANK_REFUSAL_THRESHOLD))
                .append("` 判定证据可回答；该值与当前 `EvidenceDecisionPolicy` 的强证据阈值一致\n")
                .append(String.format(Locale.ROOT, "- 可回答问题接受率：`%.4f`%n",
                        result.thresholdDecision().answerableAcceptanceRate()))
                .append(String.format(Locale.ROOT, "- %d 条应拒答问题正确率：`%.4f`%n", refusalCount,
                        result.thresholdDecision().refusalAccuracy()))
                .append(String.format(Locale.ROOT, "- %d 题阈值决策准确率：`%.4f`%n", queryCount,
                        result.thresholdDecision().overallAccuracy()))
                .append("\n## 项目复合证据策略\n\n")
                .append("- 信号：Reranker Top1/分差、强证据数、词项覆盖、精确标识符和引用完整性\n")
                .append(String.format(Locale.ROOT, "- 可回答问题接受率：`%.4f`%n",
                        result.compositeDecision().answerableAcceptanceRate()))
                .append(String.format(Locale.ROOT, "- %d 条应拒答问题拒答正确率：`%.4f`%n", refusalCount,
                        result.compositeDecision().refusalAccuracy()))
                .append(String.format(Locale.ROOT, "- %d 题复合决策准确率：`%.4f`%n", queryCount,
                        result.compositeDecision().overallAccuracy()))
                .append("\n## Dense 完全未命中样例\n\n")
                .append(missedCases(result.denseCases(), "Dense"))
                .append("\n## Hybrid 完全未命中样例\n\n")
                .append(missedCases(result.hybridCases(), "Hybrid"))
                .append("\n## Hybrid + Reranker 完全未命中样例\n\n")
                .append(missedCases(result.rerankedCases(), "Reranker"))
                .append("\n## 结论边界\n\n")
                .append("- Dense/Hybrid/Reranker 指标是检索证据，不等于最终回答正确率。\n")
                .append("- 固定阈值拒答只验证单一相关度信号；生产决策还会结合精确匹配、覆盖率、候选差距、有效 Chunk 和引用完整性。\n")
                .append("- 扩大集是人工策划、模板生成的压力集，只用于暴露检索弱点，不冒充真实用户分布。\n")
                .append("- 本报告没有调用HY3生成模型，因此不能推出最终回答正确率。\n");
        return out.toString();
    }

    private void assertMetricRange(ChannelMetrics metrics) {
        assertTrue(metrics.recallAt5() >= 0D && metrics.recallAt5() <= 1D);
        assertTrue(metrics.mrr() >= 0D && metrics.mrr() <= 1D);
        assertTrue(metrics.ndcgAt10() >= 0D && metrics.ndcgAt10() <= 1D);
        assertTrue(metrics.citationHitRate() >= 0D && metrics.citationHitRate() <= 1D);
    }

    private String expandedMetricRow(String name, double dense, double hybrid, double reranked) {
        return String.format(Locale.ROOT, "| %s | %.4f | %.4f | %.4f |%n",
                name, dense, hybrid, reranked);
    }

    private String categoryMetrics(EvaluationResult result) {
        Set<String> categories = result.rerankedCases().stream()
                .filter(item -> !item.shouldRefuse())
                .map(ChannelCase::category)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categories.isEmpty()) return "没有可回答题型。\n";
        StringBuilder out = new StringBuilder()
                .append("| 题型 | 数量 | Dense R@5 / MRR | Hybrid R@5 / MRR | Rerank R@5 / MRR |\n")
                .append("| --- | ---: | ---: | ---: | ---: |\n");
        for (String category : categories) {
            List<ChannelCase> dense = casesByCategory(result.denseCases(), category);
            List<ChannelCase> hybrid = casesByCategory(result.hybridCases(), category);
            List<ChannelCase> reranked = casesByCategory(result.rerankedCases(), category);
            ChannelMetrics denseMetrics = summarize(dense);
            ChannelMetrics hybridMetrics = summarize(hybrid);
            ChannelMetrics rerankedMetrics = summarize(reranked);
            out.append(String.format(Locale.ROOT,
                    "| %s | %d | %.4f / %.4f | %.4f / %.4f | %.4f / %.4f |%n",
                    category.replace("|", "\\|"), reranked.size(),
                    denseMetrics.recallAt5(), denseMetrics.mrr(),
                    hybridMetrics.recallAt5(), hybridMetrics.mrr(),
                    rerankedMetrics.recallAt5(), rerankedMetrics.mrr()));
        }
        return out.toString();
    }

    private List<ChannelCase> casesByCategory(List<ChannelCase> cases, String category) {
        return cases.stream().filter(item -> !item.shouldRefuse() && category.equals(item.category())).toList();
    }

    private String metricRow(
            String name,
            double legacy,
            double bm25,
            double dense,
            double hybrid,
            double reranked) {
        return String.format(Locale.ROOT, "| %s | %.4f | %.4f | %.4f | %.4f | %.4f |%n",
                name, legacy, bm25, dense, hybrid, reranked);
    }

    private String missedCases(List<ChannelCase> cases, String channel) {
        List<ChannelCase> missed = cases.stream()
                .filter(item -> !item.shouldRefuse() && item.reciprocalRank() == 0D)
                .limit(20)
                .toList();
        if (missed.isEmpty()) return "可回答问题全部命中。\n";
        StringBuilder out = new StringBuilder()
                .append("| ID | 问题 | 期望文章 | ").append(channel).append(" Top10 |\n")
                .append("| --- | --- | --- | --- |\n");
        missed.forEach(item -> out.append("| ").append(item.id()).append(" | ")
                .append(item.question().replace("|", "\\|")).append(" | ")
                .append(item.relevant()).append(" | ")
                .append(item.rankedIds().stream().limit(10).toList()).append(" |\n"));
        return out.toString();
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
            Long currentArticleId,
            String category,
            boolean shouldRefuse) {
    }

    private record ChannelCase(
            String id,
            String question,
            String category,
            Set<Long> relevant,
            List<Long> rankedIds,
            boolean shouldRefuse,
            double recallAt5,
            double reciprocalRank,
            double ndcgAt10,
            boolean citationHit,
            float topScore) {
    }

    private record ChannelMetrics(
            double recallAt5,
            double mrr,
            double ndcgAt10,
            double citationHitRate,
            double unsupportedAnswerRate) {
    }

    private record EvaluationResult(
            ChannelMetrics dense,
            ChannelMetrics hybrid,
            ChannelMetrics reranked,
            DecisionMetrics thresholdDecision,
            DecisionMetrics compositeDecision,
            List<ChannelCase> denseCases,
            List<ChannelCase> hybridCases,
            List<ChannelCase> rerankedCases) {
    }

    private record DecisionMetrics(
            double answerableAcceptanceRate,
            double refusalAccuracy,
            double overallAccuracy) {
    }
}
