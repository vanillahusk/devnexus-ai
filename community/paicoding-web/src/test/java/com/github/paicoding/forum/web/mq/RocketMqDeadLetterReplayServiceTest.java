package com.github.paicoding.forum.web.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.notify.service.DlqReplayAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class RocketMqDeadLetterReplayServiceTest {
    private final MessageQueueService messageQueueService = mock(MessageQueueService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DlqReplayAuditService auditService = mock(DlqReplayAuditService.class);
    private final RocketMqDeadLetterReplayService service =
            new RocketMqDeadLetterReplayService(messageQueueService, objectMapper, auditService);

    @Test
    void shouldPublishCorrectedCommentWithNewEventIdAndOriginalLink() throws Exception {
        RocketMqDeadLetterReplayService.ReplayRequest request = validRequest();

        RocketMqDeadLetterReplayService.ReplayResult result = service.replayCorrected(request, 7L);

        ArgumentCaptor<MessageQueueEvent<?>> eventCaptor = ArgumentCaptor.forClass(MessageQueueEvent.class);
        verify(messageQueueService).publish(eventCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(CommonConstants.ROCKETMQ_TAG_COMMENT_WRITE));
        assertNotEquals("poison-comment-20260712", eventCaptor.getValue().getEventId());
        assertEquals("poison-comment-20260712", eventCaptor.getValue().getOriginalEventId());
        assertEquals(eventCaptor.getValue().getEventId(), result.eventId());
        assertEquals(MessageQueueEvent.CURRENT_EVENT_VERSION, eventCaptor.getValue().getEventVersion());
        assertEquals(14L, ((com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq)
                eventCaptor.getValue().getContent()).getArticleId());
        assertEquals(true, result.submitted());
        verify(auditService).markSubmitted(result.eventId());
        ArgumentCaptor<DlqReplayAuditService.ReplayAuditCommand> audit =
                ArgumentCaptor.forClass(DlqReplayAuditService.ReplayAuditCommand.class);
        verify(auditService).begin(audit.capture());
        assertEquals("poison-comment-20260712", audit.getValue().originalEventId());
        assertEquals("comment:article:14", audit.getValue().businessKey());
        assertEquals(7L, audit.getValue().operatorId());
    }

    @Test
    void shouldRejectUnapprovedTag() throws Exception {
        RocketMqDeadLetterReplayService.ReplayRequest request = validRequest();
        request.setTag("arbitrary-topic");

        assertThrows(IllegalArgumentException.class, () -> service.replayCorrected(request, 7L));
    }

    @Test
    void shouldPublishCorrectedArticleKnowledgeWithNewEventIdAndOriginalLink() {
        RocketMqDeadLetterReplayService.ArticleKnowledgeReplayRequest request =
                new RocketMqDeadLetterReplayService.ArticleKnowledgeReplayRequest();
        request.setOriginalMsgId("knowledge-dlq-msg-1");
        request.setEventId("knowledge-event-original");
        request.setArticleId(18L);
        request.setArticleVersion(6L);
        request.setOperation(ArticleKnowledgeOperationEnum.UPDATE);
        request.setReason("fixed article metadata and retry indexing");

        RocketMqDeadLetterReplayService.ReplayResult result =
                service.replayCorrectedArticleKnowledge(request, 7L);

        ArgumentCaptor<ArticleKnowledgeEvent> eventCaptor = ArgumentCaptor.forClass(ArticleKnowledgeEvent.class);
        verify(messageQueueService).publish(
                org.mockito.ArgumentMatchers.eq(CommonConstants.ROCKETMQ_TOPIC_ARTICLE_KNOWLEDGE),
                org.mockito.ArgumentMatchers.eq(CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1),
                eventCaptor.capture(), org.mockito.ArgumentMatchers.anyString());
        assertNotEquals("knowledge-event-original", eventCaptor.getValue().getEventId());
        assertEquals("knowledge-event-original", eventCaptor.getValue().getOriginalEventId());
        assertEquals(eventCaptor.getValue().getEventId(), result.eventId());
        assertEquals(18L, eventCaptor.getValue().getArticleId());
        assertEquals(6L, eventCaptor.getValue().getArticleVersion());
        assertEquals(true, result.submitted());
        verify(auditService).markSubmitted(result.eventId());
    }

    @Test
    void shouldRejectIncompleteArticleKnowledgeReplay() {
        RocketMqDeadLetterReplayService.ArticleKnowledgeReplayRequest request =
                new RocketMqDeadLetterReplayService.ArticleKnowledgeReplayRequest();
        request.setOriginalMsgId("msg");
        request.setEventId("event");
        request.setArticleId(1L);
        request.setArticleVersion(1L);
        request.setReason("reviewed");

        assertThrows(IllegalArgumentException.class,
                () -> service.replayCorrectedArticleKnowledge(request, 7L));
    }

    @Test
    void shouldPersistFailedAuditWhenBrokerPublishFails() throws Exception {
        RocketMqDeadLetterReplayService.ReplayRequest request = validRequest();
        RuntimeException brokerFailure = new RuntimeException("broker unavailable");
        doThrow(brokerFailure).when(messageQueueService).publish(
                org.mockito.ArgumentMatchers.any(MessageQueueEvent.class),
                org.mockito.ArgumentMatchers.eq(CommonConstants.ROCKETMQ_TAG_COMMENT_WRITE));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.replayCorrected(request, 7L));

        assertEquals(brokerFailure, thrown);
        ArgumentCaptor<DlqReplayAuditService.ReplayAuditCommand> audit =
                ArgumentCaptor.forClass(DlqReplayAuditService.ReplayAuditCommand.class);
        verify(auditService).begin(audit.capture());
        verify(auditService).markFailed(audit.getValue().correctionEventId(), brokerFailure);
    }

    private RocketMqDeadLetterReplayService.ReplayRequest validRequest() throws Exception {
        RocketMqDeadLetterReplayService.ReplayRequest request = new RocketMqDeadLetterReplayService.ReplayRequest();
        request.setOriginalMsgId("AC13000304017D4991AD399B9C380000");
        request.setEventId("poison-comment-20260712");
        request.setTag(CommonConstants.ROCKETMQ_TAG_COMMENT_WRITE);
        request.setNotifyType(NotifyTypeEnum.COMMENT);
        request.setUserId(7L);
        request.setContent(objectMapper.readTree("{\"articleId\":14,\"userId\":7,\"commentContent\":\"corrected\",\"parentCommentId\":0,\"topCommentId\":0}"));
        request.setReason("fixed missing content after DLQ review");
        return request;
    }
}
