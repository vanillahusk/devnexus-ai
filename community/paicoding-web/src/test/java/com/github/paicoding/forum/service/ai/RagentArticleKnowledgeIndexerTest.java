package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.api.model.vo.article.dto.ArticleDTO;
import com.github.paicoding.forum.service.ai.index.RagentArticleKnowledgeIndexer;
import com.github.paicoding.forum.service.ai.index.ArticleMarkdownSanitizer;
import com.github.paicoding.forum.service.ai.service.RagentKnowledgeSyncService;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagentArticleKnowledgeIndexerTest {
    private final ArticleDao articleDao = mock(ArticleDao.class);
    private final RagentKnowledgeSyncService syncService = mock(RagentKnowledgeSyncService.class);
    private final RagentArticleKnowledgeIndexer indexer =
            new RagentArticleKnowledgeIndexer(articleDao, syncService, new ArticleMarkdownSanitizer());

    @Test
    void shouldBuildVersionedArticleDocumentForRagent() {
        ArticleDO article = article(12L, 4L, PushStatusEnum.ONLINE.getCode(), YesOrNoEnum.NO.getCode());
        ArticleDTO detail = new ArticleDTO();
        detail.setContent("## 事务消息\n正文");
        when(articleDao.getById(12L)).thenReturn(article);
        when(articleDao.queryArticleDetail(12L)).thenReturn(detail);

        var result = indexer.converge(12L, 4L, ArticleKnowledgeOperationEnum.ONLINE);

        ArgumentCaptor<AiKnowledgeDocDTO> captor = ArgumentCaptor.forClass(AiKnowledgeDocDTO.class);
        verify(syncService).sync(captor.capture());
        AiKnowledgeDocDTO doc = captor.getValue();
        assertEquals("article:12", doc.getKey());
        assertEquals("article-12-v4", doc.getCode());
        assertEquals("---\nsourceType: ARTICLE\narticleId: 12\narticleVersion: 4\nstatus: ONLINE\n"
                + "title: RocketMQ可靠消息\n---\n"
                + "# RocketMQ可靠消息\n\n## 事务消息\n正文", doc.getExportMarkdown());
        assertEquals(4L, result.articleVersion());
        assertEquals(ArticleKnowledgeOperationEnum.ONLINE, result.operation());
    }

    @Test
    void shouldRemoveCurrentOfflineSnapshot() {
        when(articleDao.getById(12L)).thenReturn(
                article(12L, 5L, PushStatusEnum.OFFLINE.getCode(), YesOrNoEnum.NO.getCode()));
        var result = indexer.converge(12L, 5L, ArticleKnowledgeOperationEnum.OFFLINE);

        verify(syncService).deleteStrictByConfigKey("article:12");
        verify(syncService, never()).sync(org.mockito.ArgumentMatchers.any());
        assertEquals(5L, result.articleVersion());
        assertEquals(ArticleKnowledgeOperationEnum.OFFLINE, result.operation());
    }

    @Test
    void shouldConvergeOldEventDirectlyToLatestOnlineSnapshot() {
        ArticleDO latest = article(12L, 6L, PushStatusEnum.ONLINE.getCode(), YesOrNoEnum.NO.getCode());
        ArticleDTO detail = new ArticleDTO();
        detail.setContent("最新正文");
        when(articleDao.getById(12L)).thenReturn(latest);
        when(articleDao.queryArticleDetail(12L)).thenReturn(detail);

        var result = indexer.converge(12L, 5L, ArticleKnowledgeOperationEnum.UPDATE);

        ArgumentCaptor<AiKnowledgeDocDTO> captor = ArgumentCaptor.forClass(AiKnowledgeDocDTO.class);
        verify(syncService).sync(captor.capture());
        assertEquals("article-12-v6", captor.getValue().getCode());
        assertEquals(6L, result.articleVersion());
        assertEquals(ArticleKnowledgeOperationEnum.UPDATE, result.operation());
    }

    @Test
    void shouldRetryWhenFactSourceIsBehindEvent() {
        when(articleDao.getById(12L)).thenReturn(
                article(12L, 4L, PushStatusEnum.ONLINE.getCode(), YesOrNoEnum.NO.getCode()));

        assertThrows(IllegalStateException.class,
                () -> indexer.converge(12L, 5L, ArticleKnowledgeOperationEnum.UPDATE));
        verify(syncService, never()).sync(org.mockito.ArgumentMatchers.any());
    }

    private ArticleDO article(Long id, Long version, Integer status, Integer deleted) {
        ArticleDO article = new ArticleDO();
        article.setId(id);
        article.setTitle("RocketMQ可靠消息");
        article.setKnowledgeVersion(version);
        article.setStatus(status);
        article.setDeleted(deleted);
        return article;
    }
}
