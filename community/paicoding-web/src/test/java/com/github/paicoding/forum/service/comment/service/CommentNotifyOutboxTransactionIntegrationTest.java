package com.github.paicoding.forum.service.comment.service;

import com.github.paicoding.forum.api.model.enums.CommentAuditStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.service.comment.repository.dao.CommentDao;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.rank.service.UserActivityRankService;
import com.github.paicoding.forum.service.user.service.UserFootService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 使用真实 MySQL 验证评论审核与通知 Outbox 的事务原子性。
 */
@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=none",
        "spring.liquibase.enabled=false",
        "paicoding.mq.outbox.flush-delay-ms=3600000"
})
@EnabledIfSystemProperty(named = "comment.notify.outbox.integration.enabled", matches = "true")
class CommentNotifyOutboxTransactionIntegrationTest {
    private static final long TEST_ARTICLE_ID = 9_000_000_101L;
    private static final long TEST_ARTICLE_AUTHOR_ID = 1L;
    private static final long TEST_COMMENT_USER_ID = 7L;

    @Autowired
    private CommentWriteService commentWriteService;

    @Autowired
    private CommentDao commentDao;

    @SpyBean
    private MqOutboxEventDao outboxEventDao;

    @MockBean
    private ArticleReadService articleReadService;

    @MockBean
    private UserFootService userFootService;

    @MockBean
    private CommentHotspotGovernanceService commentHotspotGovernanceService;

    @MockBean
    private MessageQueueService messageQueueService;

    @MockBean
    private UserActivityRankService userActivityRankService;

    private Long commentId;

    @BeforeEach
    void setUp() {
        ArticleDO article = new ArticleDO();
        article.setId(TEST_ARTICLE_ID);
        article.setUserId(TEST_ARTICLE_AUTHOR_ID);
        when(articleReadService.queryBasicArticle(TEST_ARTICLE_ID)).thenReturn(article);
        commentId = insertPendingComment();
    }

    @AfterEach
    void cleanUp() {
        reset(outboxEventDao);
        if (commentId != null) {
            outboxEventDao.lambdaUpdate()
                    .eq(MqOutboxEventDO::getEventId, eventId())
                    .remove();
            commentDao.removeById(commentId);
        }
    }

    @Test
    void shouldPersistApprovalAndOutboxInOneTransaction() {
        commentWriteService.reviewComment(commentId, CommentAuditStatusEnum.APPROVED);

        CommentDO approved = commentDao.getById(commentId);
        MqOutboxEventDO outbox = outboxEventDao.lambdaQuery()
                .eq(MqOutboxEventDO::getEventId, eventId())
                .one();

        assertEquals(CommentAuditStatusEnum.APPROVED.getCode(), approved.getAuditStatus());
        assertNotNull(outbox, "审核通过必须在同一事务写入通知 Outbox");
        assertEquals("comment:" + commentId, outbox.getAggregateId());
    }

    @Test
    void shouldRollbackApprovalWhenOutboxInsertFails() {
        doThrow(new IllegalStateException("injected comment outbox insert failure"))
                .when(outboxEventDao).save(any(MqOutboxEventDO.class));

        assertThrows(IllegalStateException.class,
                () -> commentWriteService.reviewComment(commentId, CommentAuditStatusEnum.APPROVED));

        reset(outboxEventDao);
        CommentDO afterFailure = commentDao.getById(commentId);
        long outboxCount = outboxEventDao.lambdaQuery()
                .eq(MqOutboxEventDO::getEventId, eventId())
                .count();
        assertEquals(CommentAuditStatusEnum.PENDING.getCode(), afterFailure.getAuditStatus(),
                "Outbox 写入失败时审核状态必须回滚为待审核");
        assertEquals(0L, outboxCount);
    }

    private Long insertPendingComment() {
        CommentDO comment = new CommentDO();
        comment.setArticleId(TEST_ARTICLE_ID);
        comment.setUserId(TEST_COMMENT_USER_ID);
        comment.setContent("comment notify outbox transaction integration test");
        comment.setParentCommentId(0L);
        comment.setTopCommentId(0L);
        comment.setAuditStatus(CommentAuditStatusEnum.PENDING.getCode());
        comment.setDeleted(YesOrNoEnum.NO.getCode());
        commentDao.save(comment);
        return comment.getId();
    }

    private String eventId() {
        return "comment-notify:" + commentId;
    }
}
