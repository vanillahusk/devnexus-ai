package com.github.paicoding.forum.service.comment.service;

import com.github.paicoding.forum.api.model.enums.CommentAuditStatusEnum;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.service.comment.converter.CommentConverter;
import com.github.paicoding.forum.service.comment.repository.dao.CommentDao;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.comment.service.impl.CommentWriteServiceImpl;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import com.github.paicoding.forum.service.user.service.UserFootService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentAuditWorkflowTest {
    private final CommentDao commentDao = mock(CommentDao.class);
    private final ArticleReadService articleReadService = mock(ArticleReadService.class);
    private final UserFootService userFootService = mock(UserFootService.class);
    private final CommentHotspotGovernanceService hotspotService = mock(CommentHotspotGovernanceService.class);
    private final CommentRateLimitService rateLimitService = mock(CommentRateLimitService.class);
    private final MessageQueueService messageQueueService = mock(MessageQueueService.class);
    private final MqOutboxService outboxService = mock(MqOutboxService.class);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final CommentWriteServiceImpl service = new CommentWriteServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "commentDao", commentDao);
        ReflectionTestUtils.setField(service, "articleReadService", articleReadService);
        ReflectionTestUtils.setField(service, "userFootWriteService", userFootService);
        ReflectionTestUtils.setField(service, "commentHotspotGovernanceService", hotspotService);
        ReflectionTestUtils.setField(service, "commentRateLimitService", rateLimitService);
        ReflectionTestUtils.setField(service, "messageQueueService", messageQueueService);
        ReflectionTestUtils.setField(service, "mqOutboxService", outboxService);
        ReflectionTestUtils.setField(service, "commentAsyncEnabled", true);
        new SpringUtil().setApplicationContext(applicationContext);
    }

    @AfterEach
    void tearDown() {
        new SpringUtil().setApplicationContext(null);
    }

    @Test
    void newCommentShouldStartPending() {
        CommentSaveReq req = new CommentSaveReq();
        req.setArticleId(10L);
        req.setUserId(20L);
        req.setCommentContent("pending");

        CommentDO comment = CommentConverter.toDo(req);

        assertEquals(CommentAuditStatusEnum.PENDING.getCode(), comment.getAuditStatus());
    }

    @Test
    void synchronousCreateShouldReturnPendingMarker() {
        CommentSaveReq req = new CommentSaveReq();
        req.setArticleId(10L);
        req.setUserId(20L);
        req.setCommentContent("pending");
        when(messageQueueService.enabled()).thenReturn(false);
        when(articleReadService.queryBasicArticle(10L)).thenReturn(new ArticleDO());
        doAnswer(invocation -> {
            CommentDO saved = invocation.getArgument(0);
            saved.setId(88L);
            return true;
        }).when(commentDao).save(any(CommentDO.class));

        Long result = service.saveComment(req);

        assertEquals(-88L, result);
        verify(rateLimitService).check(10L, null);
        verify(userFootService, never()).saveCommentFoot(any(), any(), any());
        verify(outboxService, never()).saveCommentNotify(any(), any());
        verify(applicationContext, never()).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    void disabledCommentAsyncShouldWriteSynchronouslyEvenWhenMqIsAvailable() {
        CommentSaveReq req = new CommentSaveReq();
        req.setArticleId(10L);
        req.setUserId(20L);
        req.setCommentContent("sync-fallback");
        ReflectionTestUtils.setField(service, "commentAsyncEnabled", false);
        when(messageQueueService.enabled()).thenReturn(true);
        when(articleReadService.queryBasicArticle(10L)).thenReturn(new ArticleDO());
        doAnswer(invocation -> {
            CommentDO saved = invocation.getArgument(0);
            saved.setId(89L);
            return true;
        }).when(commentDao).save(any(CommentDO.class));

        Long result = service.saveComment(req);

        assertEquals(-89L, result);
        verify(messageQueueService, never()).publish(any(), any());
    }

    @Test
    void rejectingPendingCommentShouldNotCreateBusinessSideEffects() {
        CommentDO comment = pendingComment(1L, 10L, 20L, 0L, 0L);
        when(commentDao.getById(1L)).thenReturn(comment);
        when(commentDao.transitionAuditStatus(1L, CommentAuditStatusEnum.REJECTED)).thenReturn(true);

        service.reviewComment(1L, CommentAuditStatusEnum.REJECTED);

        assertEquals(CommentAuditStatusEnum.REJECTED.getCode(), comment.getAuditStatus());
        verify(userFootService, never()).saveCommentFoot(any(), any(), any());
        verify(applicationContext, never()).publishEvent(any(ApplicationEvent.class));
        verify(hotspotService, never()).onCommentChanged(any());
    }

    @Test
    void approvingPendingTopCommentShouldActivateFootNotifyAndCache() {
        CommentDO comment = pendingComment(2L, 10L, 20L, 0L, 0L);
        ArticleDO article = new ArticleDO();
        article.setId(10L);
        article.setUserId(99L);
        when(commentDao.getById(2L)).thenReturn(comment);
        when(commentDao.transitionAuditStatus(2L, CommentAuditStatusEnum.APPROVED)).thenReturn(true);
        when(articleReadService.queryBasicArticle(10L)).thenReturn(article);

        service.reviewComment(2L, CommentAuditStatusEnum.APPROVED);

        assertEquals(CommentAuditStatusEnum.APPROVED.getCode(), comment.getAuditStatus());
        verify(userFootService).saveCommentFoot(comment, 99L, null);
        verify(outboxService).saveCommentNotify(comment, NotifyTypeEnum.COMMENT);
        ArgumentCaptor<ApplicationEvent> event = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(applicationContext).publishEvent(event.capture());
        NotifyMsgEvent<?> notify = (NotifyMsgEvent<?>) event.getValue();
        assertEquals(NotifyTypeEnum.COMMENT, notify.getNotifyType());
        verify(hotspotService).onCommentChanged(10L);
        verify(hotspotService, never()).onReplyDelta(any(), any(), anyInt());
    }

    @Test
    void approvingReplyShouldRequireVisibleParentAndIncrementReplyCount() {
        CommentDO reply = pendingComment(3L, 10L, 20L, 7L, 7L);
        CommentDO parent = pendingComment(7L, 10L, 30L, 0L, 0L);
        parent.setAuditStatus(CommentAuditStatusEnum.APPROVED.getCode());
        ArticleDO article = new ArticleDO();
        article.setUserId(99L);
        when(commentDao.getById(3L)).thenReturn(reply);
        when(commentDao.getById(7L)).thenReturn(parent);
        when(commentDao.transitionAuditStatus(3L, CommentAuditStatusEnum.APPROVED)).thenReturn(true);
        when(articleReadService.queryBasicArticle(10L)).thenReturn(article);

        service.reviewComment(3L, CommentAuditStatusEnum.APPROVED);

        verify(userFootService).saveCommentFoot(reply, 99L, 30L);
        verify(outboxService).saveCommentNotify(reply, NotifyTypeEnum.REPLY);
        verify(hotspotService).onReplyDelta(10L, 7L, 1);
        ArgumentCaptor<ApplicationEvent> event = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(applicationContext).publishEvent(event.capture());
        assertEquals(NotifyTypeEnum.REPLY, ((NotifyMsgEvent<?>) event.getValue()).getNotifyType());
    }

    private CommentDO pendingComment(Long id, Long articleId, Long userId, Long parentId, Long topId) {
        CommentDO comment = new CommentDO();
        comment.setId(id);
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setParentCommentId(parentId);
        comment.setTopCommentId(topId);
        comment.setDeleted(0);
        comment.setAuditStatus(CommentAuditStatusEnum.PENDING.getCode());
        return comment;
    }
}
