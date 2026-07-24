package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.service.ai.retrieval.LegacyRetrievalPolicy;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无外部依赖的旧召回离线基线。数据集和生产链路共享 {@link LegacyRetrievalPolicy}，
 * 用来阻止后续方案使用不同口径夸大提升。
 */
class LegacyRagBaselineEvaluatorTest {
    private static final String ARTICLES_RESOURCE = "rag/legacy-baseline-articles.tsv";
    private static final String QUERIES_RESOURCE = "rag/legacy-baseline-queries.tsv";

    @Test
    void shouldEvaluateFrozenLegacyRetrievalAndWriteMarkdownReport() throws IOException {
        List<ArticleFixture> articles = loadArticles();
        List<QueryFixture> queries = loadQueries();

        assertTrue(articles.size() >= 25, "基线语料至少需要25篇文章");
        assertTrue(queries.size() >= 50, "第一版评测集至少需要50条问题");
        assertEquals(queries.size(), queries.stream().map(QueryFixture::id).distinct().count(), "问题ID必须唯一");
        assertTrue(queries.stream().anyMatch(QueryFixture::shouldRefuse), "必须包含拒答问题");
        assertTrue(queries.stream().anyMatch(query -> query.currentArticleId() != null), "必须包含当前文章上下文");

        EvaluationResult result = evaluate(articles, queries);
        Path report = resolveReportPath();
        Files.createDirectories(report.getParent());
        Files.writeString(report, renderReport(result, articles.size(), queries.size()), StandardCharsets.UTF_8);

        assertTrue(result.recallAt5() >= 0D && result.recallAt5() <= 1D);
        assertTrue(result.mrr() >= 0D && result.mrr() <= 1D);
        assertTrue(result.ndcgAt10() >= 0D && result.ndcgAt10() <= 1D);
        assertTrue(result.citationHitRate() >= 0D && result.citationHitRate() <= 1D);
        assertTrue(result.unsupportedAnswerRate() >= 0D && result.unsupportedAnswerRate() <= 1D);
        assertFalse(result.cases().isEmpty());
        // 冻结第一版旧链路的确定性指标；时延受机器影响，不做快照断言。
        assertEquals(0.5160D, result.recallAt5(), 0.0001D);
        assertEquals(0.5288D, result.mrr(), 0.0001D);
        assertEquals(0.5175D, result.ndcgAt10(), 0.0001D);
        assertEquals(0.5385D, result.citationHitRate(), 0.0001D);
        assertEquals(0D, result.unsupportedAnswerRate(), 0.0001D);

        System.out.printf(Locale.ROOT,
                "RAG_BASELINE_METRICS cases=%d recall_at_5=%.4f mrr=%.4f ndcg_at_10=%.4f citation_hit_rate=%.4f unsupported_answer_rate=%.4f report=%s%n",
                queries.size(), result.recallAt5(), result.mrr(), result.ndcgAt10(),
                result.citationHitRate(), result.unsupportedAnswerRate(), report.toAbsolutePath());
    }

    private EvaluationResult evaluate(List<ArticleFixture> articles, List<QueryFixture> queries) {
        List<CaseResult> cases = new ArrayList<>(queries.size());
        List<Long> latencies = new ArrayList<>(queries.size());
        double recallSum = 0D;
        double reciprocalRankSum = 0D;
        double ndcgSum = 0D;
        int answerableCount = 0;
        int citationHits = 0;
        int refuseCount = 0;
        int unsupportedAnswers = 0;

        for (QueryFixture query : queries) {
            long start = System.nanoTime();
            List<ScoredArticle> ranked = retrieve(articles, query);
            long elapsedNanos = System.nanoTime() - start;
            latencies.add(elapsedNanos);

            List<Long> rankedIds = ranked.stream().map(result -> result.article().id()).toList();
            Set<Long> relevant = query.expectedArticleIds();
            double recall = relevant.isEmpty() ? 0D : intersectionSize(rankedIds.subList(0, Math.min(5, rankedIds.size())), relevant) / (double) relevant.size();
            double reciprocalRank = reciprocalRank(rankedIds, relevant);
            double ndcg = ndcgAt(rankedIds, relevant, 10);
            boolean predictedRefuse = rankedIds.isEmpty();
            boolean citationHit = !relevant.isEmpty() && rankedIds.stream().limit(6).anyMatch(relevant::contains);

            if (!query.shouldRefuse()) {
                answerableCount++;
                recallSum += recall;
                reciprocalRankSum += reciprocalRank;
                ndcgSum += ndcg;
                if (citationHit) {
                    citationHits++;
                }
            } else {
                refuseCount++;
                if (!predictedRefuse) {
                    unsupportedAnswers++;
                }
            }

            cases.add(new CaseResult(query.id(), query.question(), relevant, rankedIds,
                    predictedRefuse, recall, reciprocalRank, ndcg, elapsedNanos));
        }

        List<Long> sortedLatencies = latencies.stream().sorted().toList();
        double averageMicros = latencies.stream().mapToLong(Long::longValue).average().orElse(0D) / 1_000D;
        double p95Micros = sortedLatencies.get(Math.max(0, (int) Math.ceil(sortedLatencies.size() * 0.95D) - 1)) / 1_000D;
        return new EvaluationResult(
                answerableCount == 0 ? 0D : recallSum / answerableCount,
                answerableCount == 0 ? 0D : reciprocalRankSum / answerableCount,
                answerableCount == 0 ? 0D : ndcgSum / answerableCount,
                answerableCount == 0 ? 0D : citationHits / (double) answerableCount,
                refuseCount == 0 ? 0D : unsupportedAnswers / (double) refuseCount,
                averageMicros,
                p95Micros,
                cases);
    }

    private List<ScoredArticle> retrieve(List<ArticleFixture> articles, QueryFixture query) {
        Map<Long, ScoredArticle> merged = new LinkedHashMap<>();
        if (query.currentArticleId() != null) {
            articles.stream()
                    .filter(ArticleFixture::searchable)
                    .filter(article -> Objects.equals(article.id(), query.currentArticleId()))
                    .findFirst()
                    .ifPresent(article -> merged.put(article.id(), score(article, query.question(), 80)));
        }

        String searchKey = LegacyRetrievalPolicy.normalizeSearchKey(query.question());
        if (!searchKey.isBlank()) {
            articles.stream()
                    .filter(ArticleFixture::searchable)
                    .filter(article -> legacyMysqlLike(article, searchKey))
                    .sorted(Comparator.comparingLong(ArticleFixture::id).reversed())
                    .limit(3)
                    .map(article -> score(article, query.question(), 40))
                    .forEach(candidate -> merged.putIfAbsent(candidate.article().id(), candidate));
        }

        return merged.values().stream()
                .sorted(Comparator.comparingInt(ScoredArticle::score).reversed())
                .limit(8)
                .toList();
    }

    private boolean legacyMysqlLike(ArticleFixture article, String searchKey) {
        String normalized = searchKey.toLowerCase(Locale.ROOT);
        return contains(article.title(), normalized)
                || contains(article.shortTitle(), normalized)
                || contains(article.summary(), normalized);
    }

    private boolean contains(String source, String normalizedSearchKey) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(normalizedSearchKey);
    }

    private ScoredArticle score(ArticleFixture article, String question, int baseScore) {
        String body = Objects.toString(article.summary(), "") + "\n" + Objects.toString(article.content(), "");
        return new ScoredArticle(article, baseScore + LegacyRetrievalPolicy.score(question, article.title(), body));
    }

    private int intersectionSize(Collection<Long> ranked, Set<Long> relevant) {
        return (int) ranked.stream().filter(relevant::contains).count();
    }

    private double reciprocalRank(List<Long> ranked, Set<Long> relevant) {
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1D / (i + 1D);
            }
        }
        return 0D;
    }

    private double ndcgAt(List<Long> ranked, Set<Long> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0D;
        }
        double dcg = 0D;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (relevant.contains(ranked.get(i))) {
                dcg += 1D / log2(i + 2D);
            }
        }
        double ideal = 0D;
        for (int i = 0; i < Math.min(k, relevant.size()); i++) {
            ideal += 1D / log2(i + 2D);
        }
        return ideal == 0D ? 0D : dcg / ideal;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }

    private List<ArticleFixture> loadArticles() throws IOException {
        List<String[]> rows = loadTsv(ARTICLES_RESOURCE, 7);
        List<ArticleFixture> result = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            result.add(new ArticleFixture(Long.parseLong(row[0]), row[1], row[2], row[3], row[4],
                    Boolean.parseBoolean(row[5]), Boolean.parseBoolean(row[6])));
        }
        return result;
    }

    private List<QueryFixture> loadQueries() throws IOException {
        List<String[]> rows = loadTsv(QUERIES_RESOURCE, 7);
        List<QueryFixture> result = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            result.add(new QueryFixture(row[0], row[1], parseLongSet(row[2]), parseNullableLong(row[3]),
                    row[4], splitSet(row[5]), Boolean.parseBoolean(row[6])));
        }
        return result;
    }

    private List<String[]> loadTsv(String resource, int expectedColumns) throws IOException {
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("missing evaluation resource: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\\t", -1))
                    .collect(Collectors.toList());
            for (String[] row : rows) {
                if (row.length != expectedColumns) {
                    throw new IOException("invalid TSV column count in " + resource + ": " + Arrays.toString(row));
                }
            }
            return rows;
        }
    }

    private Set<Long> parseLongSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split("\\|"))
                .map(Long::parseLong)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> splitSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split("\\|"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Long parseNullableLong(String raw) {
        return raw == null || raw.isBlank() ? null : Long.parseLong(raw);
    }

    private Path resolveReportPath() {
        String configured = System.getProperty("rag.baseline.report");
        return configured == null || configured.isBlank()
                ? Paths.get("target", "rag", "legacy-baseline-report.md")
                : Paths.get(configured);
    }

    private String renderReport(EvaluationResult result, int articleCount, int queryCount) {
        long failedAnswerable = result.cases().stream()
                .filter(caseResult -> !caseResult.relevant().isEmpty())
                .filter(caseResult -> caseResult.reciprocalRank() == 0D)
                .count();
        StringBuilder report = new StringBuilder(4096);
        report.append("# 旧 RAG 召回离线基线报告\n\n")
                .append("生成时间：").append(OffsetDateTime.now()).append("\n\n")
                .append("> 本报告不启动 MySQL、Redis、RocketMQ、向量引擎或模型服务。它使用固定语料复现当前 MySQL LIKE、文章 ID 倒序、Top 3 和 Java 关键词打分规则。\n\n")
                .append("## 1. 数据集与算法边界\n\n")
                .append("- 文章夹具：").append(articleCount).append(" 篇（含上线、未上线和已删除状态）\n")
                .append("- 问题：").append(queryCount).append(" 条（含自然语言改写、当前文章上下文和拒答问题）\n")
                .append("- 检索范围：标题、短标题、摘要；召回后才读取正文用于打分\n")
                .append("- 当前实现不是向量 RAG，没有 Embedding、语义召回、Chunk 和 Reranker\n")
                .append("- 无依据回答率是检索代理指标：应拒答问题却召回任意文章即计为一次风险；尚未评测真实 LLM 生成\n\n")
                .append("## 2. 汇总指标\n\n")
                .append("| 指标 | 结果 |\n| --- | ---: |\n")
                .append(String.format(Locale.ROOT, "| Recall@5 | %.4f |%n", result.recallAt5()))
                .append(String.format(Locale.ROOT, "| MRR | %.4f |%n", result.mrr()))
                .append(String.format(Locale.ROOT, "| nDCG@10 | %.4f |%n", result.ndcgAt10()))
                .append(String.format(Locale.ROOT, "| 引用命中率 | %.4f |%n", result.citationHitRate()))
                .append(String.format(Locale.ROOT, "| 无依据回答风险率（代理） | %.4f |%n", result.unsupportedAnswerRate()))
                .append(String.format(Locale.ROOT, "| 平均检索耗时（纯内存，µs） | %.2f |%n", result.averageLatencyMicros()))
                .append(String.format(Locale.ROOT, "| P95检索耗时（纯内存，µs） | %.2f |%n", result.p95LatencyMicros()))
                .append("\n## 3. 失败样例\n\n")
                .append("可回答问题完全未命中：").append(failedAnswerable).append(" 条。下面最多列出 15 条，用于后续混合检索对照。\n\n")
                .append("| ID | 问题 | 期望文章 | 实际Top结果 |\n| --- | --- | --- | --- |\n");
        result.cases().stream()
                .filter(caseResult -> !caseResult.relevant().isEmpty())
                .filter(caseResult -> caseResult.reciprocalRank() == 0D)
                .limit(15)
                .forEach(caseResult -> report.append("| ").append(caseResult.id()).append(" | ")
                        .append(escapeTable(caseResult.question())).append(" | ")
                        .append(caseResult.relevant()).append(" | ")
                        .append(caseResult.rankedIds()).append(" |\n"));
        report.append("\n## 4. 复现\n\n")
                .append("```bash\n")
                .append("bash scripts/run-rag-baseline-evaluation.sh\n")
                .append("```\n\n")
                .append("后续纯向量、混合检索和混合加 Reranker 必须复用同一数据集和指标实现。\n");
        return report.toString();
    }

    private String escapeTable(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private record ArticleFixture(long id, String title, String shortTitle, String summary, String content,
                                  boolean online, boolean deleted) {
        boolean searchable() {
            return online && !deleted;
        }
    }

    private record QueryFixture(String id, String question, Set<Long> expectedArticleIds, Long currentArticleId,
                                String expectedHeading, Set<String> answerKeywords, boolean shouldRefuse) {
    }

    private record ScoredArticle(ArticleFixture article, int score) {
    }

    private record CaseResult(String id, String question, Set<Long> relevant, List<Long> rankedIds,
                              boolean predictedRefuse, double recallAt5, double reciprocalRank,
                              double ndcgAt10, long latencyNanos) {
    }

    private record EvaluationResult(double recallAt5, double mrr, double ndcgAt10,
                                    double citationHitRate, double unsupportedAnswerRate,
                                    double averageLatencyMicros, double p95LatencyMicros,
                                    List<CaseResult> cases) {
    }
}
