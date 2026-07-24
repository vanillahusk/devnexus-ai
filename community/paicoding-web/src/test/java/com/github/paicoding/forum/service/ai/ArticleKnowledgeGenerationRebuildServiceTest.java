package com.github.paicoding.forum.service.ai;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeGenerationRebuildService;
import com.github.paicoding.forum.service.ai.index.RagentArticleKnowledgeIndexer;
import com.github.paicoding.forum.service.ai.service.RagentKnowledgeSyncService;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleKnowledgeGenerationRebuildServiceTest {
    private final ArticleDao articleDao = mock(ArticleDao.class);
    private final MqOutboxEventDao outbox = mock(MqOutboxEventDao.class);
    private final RagentArticleKnowledgeIndexer indexer = mock(RagentArticleKnowledgeIndexer.class);
    private final RagentKnowledgeSyncService ragent = mock(RagentKnowledgeSyncService.class);
    private final AiKnowledgeProperties properties = properties();
    private final ArticleKnowledgeGenerationRebuildService service =
            new ArticleKnowledgeGenerationRebuildService(articleDao, outbox, indexer, ragent, properties);

    @Test
    void shouldBuildSnapshotReplayWatermarkReconcileAndActivate() {
        ArticleDO article = article(1001L, 8L);
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of(article), List.of(article));
        when(outbox.latestArticleKnowledgeWatermark()).thenReturn(10L, 11L, 11L);
        when(outbox.listArticleKnowledgeBetween(10L, 11L, 100))
                .thenReturn(List.of(outbox(11L, event(1001L, 8L))));
        when(ragent.beginGeneration("g2", 10L)).thenReturn(new RagentKnowledgeSyncService.GenerationState(
                "articles", "articles", "articles--g2", null, "BUILDING", 10, 10, 10, false));
        when(ragent.generationArticleVersions("articles--g2")).thenReturn(Map.of(
                1001L, new RagentKnowledgeSyncService.ArticleVersionSummary(8, 8, 3)));

        ArticleKnowledgeGenerationRebuildService.RebuildResult result = service.rebuild("g2");

        assertEquals(1, result.snapshotArticles());
        assertEquals(1, result.replayedEvents());
        assertEquals(11L, result.finalWatermark());
        verify(indexer).convergeToGeneration(
                1001L, 8L, ArticleKnowledgeOperationEnum.ONLINE, "articles--g2");
        verify(indexer).convergeToGeneration(
                1001L, 8L, ArticleKnowledgeOperationEnum.UPDATE, "articles--g2");
        verify(ragent).recordGenerationProgress("g2", 11L, 11L, true);
        verify(ragent).activateGeneration("g2");
        verify(ragent, never()).failGeneration("g2");
    }

    @Test
    void reconciliationMismatchMustKeepOldGenerationAndMarkRebuildFailed() {
        ArticleDO article = article(1001L, 8L);
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of(article), List.of(article));
        when(outbox.latestArticleKnowledgeWatermark()).thenReturn(10L, 10L, 10L);
        when(ragent.beginGeneration("g2", 10L)).thenReturn(new RagentKnowledgeSyncService.GenerationState(
                "articles", "articles", "articles--g2", null, "BUILDING", 10, 10, 10, false));
        when(ragent.generationArticleVersions("articles--g2")).thenReturn(Map.of());

        assertThrows(IllegalStateException.class, () -> service.rebuild("g2"));

        verify(ragent).recordGenerationProgress("g2", 10L, 10L, false);
        verify(ragent, never()).activateGeneration("g2");
        verify(ragent).failGeneration("g2");
    }

    @Test
    void outboxGapMustFailClosedWithoutAliasSwitch() {
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of());
        when(outbox.latestArticleKnowledgeWatermark()).thenReturn(10L, 11L);
        when(outbox.listArticleKnowledgeBetween(10L, 11L, 100)).thenReturn(List.of());
        when(ragent.beginGeneration("g2", 10L)).thenReturn(new RagentKnowledgeSyncService.GenerationState(
                "articles", "articles", "articles--g2", null, "BUILDING", 10, 10, 10, false));

        assertThrows(IllegalStateException.class, () -> service.rebuild("g2"));

        verify(ragent, never()).activateGeneration("g2");
        verify(ragent).failGeneration("g2");
    }

    @Test
    void shouldRepeatCatchupWhenWatermarkMovesDuringReconciliation() {
        ArticleDO article = article(1001L, 8L);
        when(articleDao.list(any(Wrapper.class))).thenReturn(
                List.of(article), List.of(article), List.of(article));
        when(outbox.latestArticleKnowledgeWatermark()).thenReturn(10L, 11L, 12L, 12L, 12L);
        when(outbox.listArticleKnowledgeBetween(10L, 11L, 100))
                .thenReturn(List.of(outbox(11L, event(1001L, 8L))));
        when(outbox.listArticleKnowledgeBetween(11L, 12L, 100))
                .thenReturn(List.of(outbox(12L, event(1001L, 8L))));
        when(ragent.beginGeneration("g2", 10L)).thenReturn(new RagentKnowledgeSyncService.GenerationState(
                "articles", "articles", "articles--g2", null, "BUILDING", 10, 10, 10, false));
        when(ragent.generationArticleVersions("articles--g2")).thenReturn(Map.of(
                1001L, new RagentKnowledgeSyncService.ArticleVersionSummary(8, 8, 3)));

        ArticleKnowledgeGenerationRebuildService.RebuildResult result = service.rebuild("g2");

        assertEquals(2, result.catchupRounds());
        assertEquals(2, result.replayedEvents());
        assertEquals(12, result.finalWatermark());
        verify(ragent).recordGenerationProgress("g2", 11L, 11L, false);
        verify(ragent).recordGenerationProgress("g2", 12L, 12L, true);
        verify(ragent).activateGeneration("g2");
    }

    private ArticleDO article(long id, long version) {
        ArticleDO article = new ArticleDO();
        article.setId(id);
        article.setKnowledgeVersion(version);
        article.setStatus(PushStatusEnum.ONLINE.getCode());
        article.setDeleted(YesOrNoEnum.NO.getCode());
        return article;
    }

    private ArticleKnowledgeEvent event(long articleId, long version) {
        return ArticleKnowledgeEvent.create(articleId, version, ArticleKnowledgeOperationEnum.UPDATE);
    }

    private MqOutboxEventDO outbox(long id, ArticleKnowledgeEvent event) {
        MqOutboxEventDO row = new MqOutboxEventDO();
        row.setId(id);
        row.setPayload(JSON.toJSONString(event));
        return row;
    }

    private AiKnowledgeProperties properties() {
        AiKnowledgeProperties value = new AiKnowledgeProperties();
        value.getGenerationRebuild().setBatchSize(100);
        value.getGenerationRebuild().setMaxCatchupRounds(3);
        value.getGenerationRebuild().setMaxArticles(1000);
        return value;
    }
}
