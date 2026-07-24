package com.github.paicoding.forum.service.ai.index;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.RagentKnowledgeSyncService;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 文章知识索引快照 + Outbox 水位追平编排器。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.knowledge.generation-rebuild.enabled", havingValue = "true")
public class ArticleKnowledgeGenerationRebuildService {
    private static final Pattern LABEL = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    private final ArticleDao articleDao;
    private final MqOutboxEventDao outboxEventDao;
    private final RagentArticleKnowledgeIndexer indexer;
    private final RagentKnowledgeSyncService ragent;
    private final AiKnowledgeProperties properties;

    public RebuildResult rebuild(String generationLabel) {
        if (generationLabel == null || !LABEL.matcher(generationLabel).matches()) {
            throw new IllegalArgumentException("generationLabel必须是1-40位小写字母、数字或连字符");
        }
        int batchSize = boundedBatchSize();
        long startWatermark = outboxEventDao.latestArticleKnowledgeWatermark();
        RagentKnowledgeSyncService.GenerationState state = ragent.beginGeneration(generationLabel, startWatermark);
        String physicalCollection = state.buildingGeneration();
        if (physicalCollection == null || physicalCollection.isBlank()) {
            throw new IllegalStateException("Ragent未返回正在构建的物理Generation");
        }
        boolean activated = false;
        try {
            int snapshotArticles = buildSnapshot(physicalCollection, batchSize);
            long appliedWatermark = startWatermark;
            int replayedEvents = 0;
            int maxRounds = Math.max(1, Math.min(properties.getGenerationRebuild().getMaxCatchupRounds(), 100));
            for (int round = 1; round <= maxRounds; round++) {
                long targetWatermark = outboxEventDao.latestArticleKnowledgeWatermark();
                ReplayResult replay = replayTo(physicalCollection, appliedWatermark, targetWatermark, batchSize);
                appliedWatermark = replay.appliedWatermark();
                replayedEvents += replay.events();

                Map<Long, Long> expected = expectedOnlineVersions(batchSize);
                Map<Long, RagentKnowledgeSyncService.ArticleVersionSummary> actual =
                        ragent.generationArticleVersions(physicalCollection);
                boolean consistent = consistent(expected, actual);
                long watermarkAfterReconcile = outboxEventDao.latestArticleKnowledgeWatermark();
                boolean stable = watermarkAfterReconcile == targetWatermark;
                ragent.recordGenerationProgress(generationLabel, appliedWatermark, targetWatermark,
                        stable && consistent);
                if (stable && consistent) {
                    ragent.activateGeneration(generationLabel);
                    activated = true;
                    return new RebuildResult(generationLabel, physicalCollection, startWatermark,
                            targetWatermark, snapshotArticles, replayedEvents, round, expected.size());
                }
                if (stable) {
                    throw new IllegalStateException("Generation对账失败: expectedArticles=" + expected.size()
                            + ", actualArticles=" + actual.size());
                }
            }
            throw new IllegalStateException("Generation在最大轮次内未追平Outbox水位");
        } catch (RuntimeException failure) {
            if (!activated) {
                try {
                    ragent.failGeneration(generationLabel);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    private int buildSnapshot(String physicalCollection, int batchSize) {
        long lastId = 0;
        int indexed = 0;
        int maxArticles = Math.max(1, properties.getGenerationRebuild().getMaxArticles());
        while (true) {
            List<ArticleDO> articles = listOnlineArticles(lastId, batchSize);
            for (ArticleDO article : articles) {
                indexer.convergeToGeneration(article.getId(), article.getKnowledgeVersion(),
                        ArticleKnowledgeOperationEnum.ONLINE, physicalCollection);
                lastId = article.getId();
                indexed++;
                if (indexed > maxArticles) {
                    throw new IllegalStateException("Generation快照文章数超过安全上限");
                }
            }
            if (articles.size() < batchSize) return indexed;
        }
    }

    private ReplayResult replayTo(String physicalCollection, long applied, long target, int batchSize) {
        int events = 0;
        long cursor = applied;
        while (cursor < target) {
            List<MqOutboxEventDO> rows = outboxEventDao.listArticleKnowledgeBetween(cursor, target, batchSize);
            if (rows.isEmpty()) {
                throw new IllegalStateException("Outbox水位存在缺口: applied=" + cursor + ", target=" + target);
            }
            for (MqOutboxEventDO row : rows) {
                ArticleKnowledgeEvent event = JSON.parseObject(row.getPayload(), ArticleKnowledgeEvent.class);
                event.validate();
                indexer.convergeToGeneration(event.getArticleId(), event.getArticleVersion(),
                        event.getOperation(), physicalCollection);
                cursor = row.getId();
                events++;
            }
        }
        return new ReplayResult(cursor, events);
    }

    private Map<Long, Long> expectedOnlineVersions(int batchSize) {
        Map<Long, Long> result = new LinkedHashMap<>();
        long lastId = 0;
        while (true) {
            List<ArticleDO> articles = listOnlineArticles(lastId, batchSize);
            for (ArticleDO article : articles) {
                result.put(article.getId(), article.getKnowledgeVersion());
                lastId = article.getId();
            }
            if (articles.size() < batchSize) return result;
        }
    }

    private List<ArticleDO> listOnlineArticles(long lastId, int batchSize) {
        return articleDao.list(Wrappers.<ArticleDO>lambdaQuery()
                .gt(ArticleDO::getId, lastId)
                .gt(ArticleDO::getKnowledgeVersion, 0L)
                .eq(ArticleDO::getStatus, PushStatusEnum.ONLINE.getCode())
                .eq(ArticleDO::getDeleted, YesOrNoEnum.NO.getCode())
                .orderByAsc(ArticleDO::getId)
                .last("LIMIT " + batchSize));
    }

    private boolean consistent(Map<Long, Long> expected,
                               Map<Long, RagentKnowledgeSyncService.ArticleVersionSummary> actual) {
        if (!Objects.equals(expected.keySet(), actual.keySet())) return false;
        return expected.entrySet().stream().allMatch(entry -> {
            RagentKnowledgeSyncService.ArticleVersionSummary summary = actual.get(entry.getKey());
            return summary != null && summary.chunkCount() > 0
                    && summary.minVersion() == entry.getValue()
                    && summary.maxVersion() == entry.getValue();
        });
    }

    private int boundedBatchSize() {
        return Math.max(1, Math.min(properties.getGenerationRebuild().getBatchSize(), 500));
    }

    private record ReplayResult(long appliedWatermark, int events) {
    }

    public record RebuildResult(String generationLabel, String physicalCollection,
                                long startWatermark, long finalWatermark,
                                int snapshotArticles, int replayedEvents,
                                int catchupRounds, int reconciledArticles) {
    }
}
