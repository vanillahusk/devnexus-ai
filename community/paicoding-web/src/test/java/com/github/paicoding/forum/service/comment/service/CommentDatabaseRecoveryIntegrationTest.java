package com.github.paicoding.forum.service.comment.service;

import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;
import com.github.paicoding.forum.service.comment.repository.dao.CommentDao;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.web.QuickForumApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=none",
        "spring.liquibase.enabled=true"
})
@EnabledIfSystemProperty(named = "comment.database.recovery.integration.enabled", matches = "true")
class CommentDatabaseRecoveryIntegrationTest {
    private static final String CONTENT = "comment-database-idempotency-integration-proof";
    private static final String EVENT_ID = "comment-integration-event-20260715";

    @Autowired
    private CommentWriteService commentWriteService;

    @Autowired
    private CommentDao commentDao;

    @MockBean
    private MessageQueueService messageQueueService;

    @AfterEach
    void cleanUp() {
        commentDao.lambdaUpdate().eq(CommentDO::getSourceEventId, EVENT_ID).remove();
    }

    @Test
    void shouldPersistSameRocketMqEventOnlyOnce() {
        cleanUp();
        CommentSaveReq request = request();

        Long firstId = commentWriteService.saveCommentFromEvent(EVENT_ID, request);
        Long duplicatedId = commentWriteService.saveCommentFromEvent(EVENT_ID, request);

        assertEquals(firstId, duplicatedId);
        CommentDO persisted = commentDao.getBySourceEventId(EVENT_ID);
        assertNotNull(persisted);
        assertEquals(CONTENT, persisted.getContent());
        assertEquals(1L, commentDao.lambdaQuery().eq(CommentDO::getSourceEventId, EVENT_ID).count());
    }

    private CommentSaveReq request() {
        CommentSaveReq request = new CommentSaveReq();
        request.setArticleId(14L);
        request.setUserId(7L);
        request.setCommentContent(CONTENT);
        request.setParentCommentId(0L);
        request.setTopCommentId(0L);
        return request;
    }
}
