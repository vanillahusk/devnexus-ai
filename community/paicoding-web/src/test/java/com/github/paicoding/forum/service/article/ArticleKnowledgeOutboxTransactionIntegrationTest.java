package com.github.paicoding.forum.service.article;

import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.vo.article.ArticlePostReq;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleSettingService;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.web.QuickForumApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 使用真实 MySQL 验证文章状态、knowledge_version 与知识索引 Outbox 的事务原子性。
 */
@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=none",
        "spring.liquibase.enabled=true",
        "paicoding.mq.outbox.flush-delay-ms=3600000"
})
@EnabledIfSystemProperty(named = "article.knowledge.outbox.integration.enabled", matches = "true")
class ArticleKnowledgeOutboxTransactionIntegrationTest {
    private static final long ARTICLE_ID = 9_000_000_301L;

    @Autowired
    private ArticleSettingService articleSettingService;

    @Autowired
    private ArticleDao articleDao;

    @SpyBean
    private MqOutboxEventDao outboxEventDao;

    @MockBean
    private MessageQueueService messageQueueService;

    @BeforeEach
    void setUp() {
        cleanUpData();
        ArticleDO article = new ArticleDO();
        article.setId(ARTICLE_ID);
        article.setUserId(1L);
        article.setArticleType(1);
        article.setTitle("knowledge transaction before");
        article.setStatus(PushStatusEnum.OFFLINE.getCode());
        article.setKnowledgeVersion(0L);
        article.setDeleted(YesOrNoEnum.NO.getCode());
        articleDao.save(article);
    }

    @AfterEach
    void tearDown() {
        reset(outboxEventDao);
        cleanUpData();
    }

    @Test
    void shouldRollbackArticleAndVersionWhenKnowledgeOutboxInsertFails() {
        doThrow(new IllegalStateException("injected article knowledge outbox failure"))
                .when(outboxEventDao).save(any(MqOutboxEventDO.class));

        ArticlePostReq request = new ArticlePostReq();
        request.setArticleId(ARTICLE_ID);
        request.setTitle("knowledge transaction after");
        request.setStatus(PushStatusEnum.ONLINE.getCode());

        assertThrows(IllegalStateException.class, () -> articleSettingService.updateArticle(request));

        reset(outboxEventDao);
        ArticleDO rolledBack = articleDao.getById(ARTICLE_ID);
        assertEquals("knowledge transaction before", rolledBack.getTitle());
        assertEquals(PushStatusEnum.OFFLINE.getCode(), rolledBack.getStatus());
        assertEquals(0L, rolledBack.getKnowledgeVersion());
        assertEquals(0L, outboxEventDao.lambdaQuery()
                .eq(MqOutboxEventDO::getAggregateId, "article:" + ARTICLE_ID).count());
    }

    private void cleanUpData() {
        outboxEventDao.lambdaUpdate()
                .eq(MqOutboxEventDO::getAggregateId, "article:" + ARTICLE_ID).remove();
        articleDao.removeById(ARTICLE_ID);
    }
}
