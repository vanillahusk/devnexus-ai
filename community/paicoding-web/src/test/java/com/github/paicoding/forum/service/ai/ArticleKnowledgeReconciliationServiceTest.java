package com.github.paicoding.forum.service.ai;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexState;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeReconciliationService;
import com.github.paicoding.forum.service.ai.repository.dao.ArticleKnowledgeIndexStateDao;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleKnowledgeReconciliationServiceTest {
    private final ArticleDao articleDao = mock(ArticleDao.class);
    private final ArticleKnowledgeIndexStateDao stateDao = mock(ArticleKnowledgeIndexStateDao.class);
    private final MqOutboxService outboxService = mock(MqOutboxService.class);
    private final ArticleKnowledgeReconciliationService service =
            new ArticleKnowledgeReconciliationService(articleDao, stateDao, outboxService);

    @Test
    void shouldRepairMissingOnlineAndStaleOfflineStatesWithDeterministicEvents() {
        ArticleDO online = article(10L, 3L, PushStatusEnum.ONLINE.getCode(), YesOrNoEnum.NO.getCode());
        ArticleDO deleted = article(11L, 5L, PushStatusEnum.ONLINE.getCode(), YesOrNoEnum.YES.getCode());
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of(online, deleted));
        when(stateDao.findState(10L)).thenReturn(null);
        when(stateDao.findState(11L)).thenReturn(
                new ArticleKnowledgeIndexState(11L, 4L, ArticleKnowledgeOperationEnum.UPDATE, "old", 1L));

        ArticleKnowledgeReconciliationService.ReconciliationResult result = service.reconcileBatch(0L, 100);

        ArgumentCaptor<ArticleKnowledgeEvent> events = ArgumentCaptor.forClass(ArticleKnowledgeEvent.class);
        verify(outboxService, org.mockito.Mockito.times(2)).saveArticleKnowledge(events.capture());
        assertEquals("article-reconcile:10:3:ONLINE", events.getAllValues().get(0).getEventId());
        assertEquals(ArticleKnowledgeOperationEnum.ONLINE, events.getAllValues().get(0).getOperation());
        assertEquals("article-reconcile:11:5:OFFLINE", events.getAllValues().get(1).getEventId());
        assertEquals(2, result.repairEvents());
    }

    @Test
    void shouldSkipArticleWhoseVersionAndVisibilityAlreadyMatch() {
        ArticleDO article = article(10L, 3L, PushStatusEnum.ONLINE.getCode(), YesOrNoEnum.NO.getCode());
        when(articleDao.list(any(Wrapper.class))).thenReturn(List.of(article));
        when(stateDao.findState(10L)).thenReturn(
                new ArticleKnowledgeIndexState(10L, 3L, ArticleKnowledgeOperationEnum.UPDATE, "done", 1L));

        ArticleKnowledgeReconciliationService.ReconciliationResult result = service.reconcileBatch(0L, 100);

        assertEquals(0, result.repairEvents());
        verify(outboxService, never()).saveArticleKnowledge(any());
    }

    private ArticleDO article(Long id, Long version, Integer status, Integer deleted) {
        ArticleDO article = new ArticleDO();
        article.setId(id);
        article.setKnowledgeVersion(version);
        article.setStatus(status);
        article.setDeleted(deleted);
        return article;
    }
}
