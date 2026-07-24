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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用 PaiCoding 冻结题集评估现代关键词通道。该测试不启动数据库或模型，且不把结果冒充为
 * Dense/Hybrid 质量；它只用于和旧 MySQL LIKE 召回建立相同口径的离线对照。
 */
class ModernBm25OfflineEvaluatorTest {
    private static final double LEGACY_RECALL_AT_5 = 0.5160D;
    private static final double LEGACY_MRR = 0.5288D;

    @Test
    void shouldEvaluateBm25AgainstFrozenPaicodingDataset() throws IOException {
        Path articlesPath = requiredPath("rag.eval.articles");
        Path queriesPath = requiredPath("rag.eval.queries");
        Path reportPath = requiredPath("rag.eval.report");
        String profile = System.getProperty("rag.eval.profile", "legacy");
        boolean expandedProfile = "expanded".equalsIgnoreCase(profile);
        List<ArticleFixture> articles = loadArticles(articlesPath);
        List<QueryFixture> queries = loadQueries(queriesPath);

        assertTrue(articles.size() >= (expandedProfile ? 140 : 25), "评测语料规模不足");
        assertTrue(queries.size() >= (expandedProfile ? 140 : 50), "评测问题规模不足");
        assertEquals(queries.size(), queries.stream().map(QueryFixture::id).distinct().count(), "问题ID必须唯一");

        EvaluationResult result = evaluate(articles, queries, !expandedProfile);
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        Files.writeString(reportPath, renderReport(result, articles.size(), queries.size(), profile),
                StandardCharsets.UTF_8);

        if (!expandedProfile) {
            assertTrue(result.recallAt5() >= LEGACY_RECALL_AT_5,
                    () -> "BM25 Recall@5低于旧基线，详见 " + reportPath.toAbsolutePath());
            assertTrue(result.mrr() >= LEGACY_MRR,
                    () -> "BM25 MRR低于旧基线，详见 " + reportPath.toAbsolutePath());
        } else {
            assertTrue(result.recallAt5() >= 0D && result.recallAt5() <= 1D);
            assertTrue(result.mrr() >= 0D && result.mrr() <= 1D);
        }
        System.out.printf(Locale.ROOT,
                "MODERN_BM25_METRICS cases=%d recall_at_5=%.4f mrr=%.4f ndcg_at_10=%.4f citation_hit_rate=%.4f unsupported_proxy=%.4f report=%s%n",
                queries.size(), result.recallAt5(), result.mrr(), result.ndcgAt10(),
                result.citationHitRate(), result.unsupportedAnswerRate(), reportPath.toAbsolutePath());
    }

    private EvaluationResult evaluate(
            List<ArticleFixture> articles,
            List<QueryFixture> queries,
            boolean applyCurrentArticlePrior) {
        List<RetrievedChunk> corpus = articles.stream().filter(ArticleFixture::searchable)
                .map(article -> new RetrievedChunk(Long.toString(article.id()), article.searchText(), 0F,
                        Map.of("articleId", Long.toString(article.id()))))
                .toList();
        Bm25Scorer scorer = new Bm25Scorer();
        List<CaseResult> cases = new ArrayList<>(queries.size());
        double recall = 0D;
        double mrr = 0D;
        double ndcg = 0D;
        int answerable = 0;
        int citationHits = 0;
        int refuseCases = 0;
        int unsupported = 0;

        for (QueryFixture query : queries) {
            List<Long> ranked = new ArrayList<>(scorer.score(query.question(), corpus, 10).stream()
                    .map(chunk -> Long.parseLong(chunk.getId())).toList());
            if (applyCurrentArticlePrior && query.currentArticleId() != null
                    && articles.stream().anyMatch(article -> article.searchable() && article.id() == query.currentArticleId())) {
                ranked.remove(query.currentArticleId());
                ranked.add(0, query.currentArticleId());
                if (ranked.size() > 10) ranked = new ArrayList<>(ranked.subList(0, 10));
            }
            if (query.shouldRefuse()) {
                refuseCases++;
                if (!ranked.isEmpty()) unsupported++;
            } else {
                answerable++;
                double caseRecall = intersectionSize(ranked.stream().limit(5).toList(), query.expectedArticleIds())
                        / (double) query.expectedArticleIds().size();
                double caseMrr = reciprocalRank(ranked, query.expectedArticleIds());
                double caseNdcg = ndcgAt(ranked, query.expectedArticleIds(), 10);
                recall += caseRecall;
                mrr += caseMrr;
                ndcg += caseNdcg;
                if (ranked.stream().limit(6).anyMatch(query.expectedArticleIds()::contains)) citationHits++;
                cases.add(new CaseResult(query.id(), query.question(), query.category(),
                        query.expectedArticleIds(), ranked, caseRecall, caseMrr, caseNdcg));
            }
        }
        return new EvaluationResult(recall / answerable, mrr / answerable, ndcg / answerable,
                citationHits / (double) answerable, unsupported / (double) refuseCases, cases);
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
        for (int i = 0; i < ranked.size(); i++) if (relevant.contains(ranked.get(i))) return 1D / (i + 1D);
        return 0D;
    }

    private double ndcgAt(List<Long> ranked, Set<Long> relevant, int k) {
        double dcg = 0D;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (relevant.contains(ranked.get(i))) dcg += 1D / log2(i + 2D);
        }
        double ideal = 0D;
        for (int i = 0; i < Math.min(k, relevant.size()); i++) ideal += 1D / log2(i + 2D);
        return ideal == 0D ? 0D : dcg / ideal;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }

    private Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing system property: " + property);
        return Path.of(value);
    }

    private String renderReport(EvaluationResult result, int articleCount, int queryCount, String profile) {
        boolean expandedProfile = "expanded".equalsIgnoreCase(profile);
        long missed = result.cases().stream().filter(item -> item.reciprocalRank() == 0D).count();
        StringBuilder out = new StringBuilder(4096);
        out.append(expandedProfile
                        ? "# 扩大规模困难集 BM25 免费预检报告\n\n"
                        : "# 现代 RAG BM25 离线评测报告\n\n")
                .append("生成时间：").append(OffsetDateTime.now()).append("\n\n")
                .append(expandedProfile
                        ? "> 本报告只对独立合成困难集做零外部API的BM25预检，用于确认扩大集不是送分题。\n\n"
                        : "> 本报告复用旧 RAG 的冻结语料、问题和指标口径，仅评估 Ragent BM25 关键词通道。未使用真实语义 Embedding。\n\n")
                .append("## 数据与边界\n\n")
                .append("- 文章：").append(articleCount).append(" 篇；问题：").append(queryCount).append(" 条\n")
                .append("- Profile：`").append(profile).append("`\n")
                .append("- 仅检索 ONLINE 且未删除文章\n")
                .append(expandedProfile ? "- 不使用currentArticleId置顶先验\n" : "")
                .append("- 离线评测将每篇文章合并为一个候选文档；生产环境实际按结构化 Chunk 检索\n")
                .append("- 拒答风险仅以‘无关问题是否仍有关键词命中’代理，不代表真实回答行为\n\n")
                .append("## 指标对比\n\n")
                .append("| 指标 | 旧 MySQL LIKE 基线 | 新 BM25 | 是否不低于旧基线 |\n")
                .append("| --- | ---: | ---: | --- |\n")
                .append(metricRow("Recall@5", LEGACY_RECALL_AT_5, result.recallAt5()))
                .append(metricRow("MRR", LEGACY_MRR, result.mrr()))
                .append(metricRow("nDCG@10", 0.5175D, result.ndcgAt10()))
                .append(metricRow("引用命中率", 0.5385D, result.citationHitRate()))
                .append(String.format(Locale.ROOT, "| 无依据回答风险率（代理） | 0.0000 | %.4f | 仅记录，不作生成质量结论 |%n", result.unsupportedAnswerRate()))
                .append("\n## 分题型结果\n\n")
                .append(categoryMetrics(result.cases()))
                .append("\n## 完全未命中样例\n\n")
                .append("可回答问题完全未命中：").append(missed).append(" 条。\n\n")
                .append("| ID | 问题 | 期望文章 | BM25 Top10 |\n| --- | --- | --- | --- |\n");
        result.cases().stream().filter(item -> item.reciprocalRank() == 0D).limit(20)
                .forEach(item -> out.append("| ").append(item.id()).append(" | ")
                        .append(item.question().replace("|", "\\|")).append(" | ")
                        .append(item.relevant()).append(" | ").append(item.rankedIds()).append(" |\n"));
        out.append("\n## 结论边界\n\n")
                .append(expandedProfile
                        ? "本报告不设置质量通过线，只判断数据和指标可重复生成。困难集是人工策划、模板生成的压力集，不代表真实用户分布。\n"
                        : "通过门槛只约束 BM25 的 Recall@5 与 MRR 不低于旧基线。Hybrid 验收仍需真实语义 Embedding 复测。\n");
        return out.toString();
    }

    private String categoryMetrics(List<CaseResult> cases) {
        Set<String> categories = cases.stream().map(CaseResult::category)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        StringBuilder out = new StringBuilder()
                .append("| 题型 | 数量 | Recall@5 | MRR |\n")
                .append("| --- | ---: | ---: | ---: |\n");
        for (String category : categories) {
            List<CaseResult> categoryCases = cases.stream()
                    .filter(item -> category.equals(item.category())).toList();
            out.append(String.format(Locale.ROOT, "| %s | %d | %.4f | %.4f |%n",
                    category.replace("|", "\\|"), categoryCases.size(),
                    categoryCases.stream().mapToDouble(CaseResult::recallAt5).average().orElse(0D),
                    categoryCases.stream().mapToDouble(CaseResult::reciprocalRank).average().orElse(0D)));
        }
        return out.toString();
    }

    private String metricRow(String name, double legacy, double current) {
        return String.format(Locale.ROOT, "| %s | %.4f | %.4f | %s |%n", name, legacy, current,
                current >= legacy ? "是" : "否");
    }

    private record ArticleFixture(long id, String title, String shortTitle, String summary, String content,
                                  boolean online, boolean deleted) {
        boolean searchable() { return online && !deleted; }
        String searchText() { return String.join("\n", title, shortTitle, summary, content); }
    }

    private record QueryFixture(String id, String question, Set<Long> expectedArticleIds, Long currentArticleId,
                                String category, boolean shouldRefuse) {}

    private record CaseResult(String id, String question, String category,
                              Set<Long> relevant, List<Long> rankedIds,
                              double recallAt5, double reciprocalRank, double ndcgAt10) {}

    private record EvaluationResult(double recallAt5, double mrr, double ndcgAt10,
                                    double citationHitRate, double unsupportedAnswerRate,
                                    List<CaseResult> cases) {}
}
