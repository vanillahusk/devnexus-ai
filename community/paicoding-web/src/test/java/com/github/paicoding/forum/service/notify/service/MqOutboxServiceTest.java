package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqOutboxServiceTest {
    private final MqOutboxEventDao dao = mock(MqOutboxEventDao.class);
    private final MessageQueueService queueService = mock(MessageQueueService.class);
    private final MqReliabilityMetrics metrics = mock(MqReliabilityMetrics.class);
    private final MqOutboxService service = new MqOutboxService(dao, queueService, metrics);

    @Test
    void shouldMarkClaimedEventSent() {
        MqOutboxEventDO event = event(1L, 0);
        when(dao.listDispatchable(eq(100), any())).thenReturn(List.of(event));
        when(dao.claim(eq(event), any())).thenReturn(true);

        service.dispatch();

        verify(queueService).publish(any(MessageQueueEvent.class), eq("notify"));
        verify(dao).markSent(1L);
        verify(metrics).recordSuccess(event);
    }

    @Test
    void shouldScheduleRetryWhenPublishFails() {
        MqOutboxEventDO event = event(2L, 0);
        when(dao.listDispatchable(eq(100), any())).thenReturn(List.of(event));
        when(dao.claim(eq(event), any())).thenReturn(true);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(queueService).publish(any(MessageQueueEvent.class), eq("notify"));

        service.dispatch();

        ArgumentCaptor<Date> nextRetry = ArgumentCaptor.forClass(Date.class);
        verify(dao).markFailed(eq(2L), eq(2), eq(1), nextRetry.capture(), eq("broker unavailable"));
        verify(metrics).recordFailure(false);
        assertTrue(nextRetry.getValue().after(new Date(System.currentTimeMillis() - 1_000L)));
    }

    @Test
    void shouldMoveEventToDeadAfterMaximumRetries() {
        MqOutboxEventDO event = event(3L, 9);
        when(dao.listDispatchable(eq(100), any())).thenReturn(List.of(event));
        when(dao.claim(eq(event), any())).thenReturn(true);
        doThrow(new IllegalStateException("still unavailable"))
                .when(queueService).publish(any(MessageQueueEvent.class), eq("notify"));

        service.dispatch();

        ArgumentCaptor<Date> nextRetry = ArgumentCaptor.forClass(Date.class);
        verify(dao).markFailed(eq(3L), eq(4), eq(10), nextRetry.capture(), eq("still unavailable"));
        verify(metrics).recordFailure(true);
        assertNull(nextRetry.getValue());
    }

    @Test
    void shouldExposeNamedStatusAndDelegateDeadReplay() {
        when(dao.countByStatus()).thenReturn(Map.of(2, 3L, 3, 20L, 4, 1L));
        when(dao.replayDead(9L)).thenReturn(true);

        MqOutboxService.OutboxStatus status = service.status();

        assertEquals(0L, status.counts().get("pending"));
        assertEquals(3L, status.counts().get("retry"));
        assertEquals(20L, status.counts().get("sent"));
        assertEquals(1L, status.counts().get("dead"));
        assertTrue(service.replayDead(9L));
    }

    @Test
    void shouldPersistDeterministicCommentNotifyEvent() {
        CommentDO comment = new CommentDO();
        comment.setId(88L);
        comment.setArticleId(10L);
        comment.setUserId(20L);
        comment.setContent("approved comment");

        service.saveCommentNotify(comment, NotifyTypeEnum.COMMENT);

        ArgumentCaptor<MqOutboxEventDO> outbox = ArgumentCaptor.forClass(MqOutboxEventDO.class);
        verify(dao).save(outbox.capture());
        MqOutboxEventDO saved = outbox.getValue();
        assertEquals("comment-notify:88", saved.getEventId());
        assertEquals("comment:88", saved.getAggregateId());
        assertEquals("notify", saved.getTag());
        MessageQueueEvent<?> event = JsonUtil.toObj(saved.getPayload(), MessageQueueEvent.class);
        assertEquals("comment-notify:88", event.getEventId());
        assertEquals(NotifyTypeEnum.COMMENT, event.getNotifyType());
    }

    @Test
    void shouldPersistAndDispatchArticleKnowledgeOnIndependentTopic() {
        ArticleKnowledgeEvent payload = ArticleKnowledgeEvent.create(
                10L, 3L, ArticleKnowledgeOperationEnum.UPDATE);
        service.saveArticleKnowledge(payload);

        ArgumentCaptor<MqOutboxEventDO> persisted = ArgumentCaptor.forClass(MqOutboxEventDO.class);
        verify(dao).save(persisted.capture());
        MqOutboxEventDO saved = persisted.getValue();
        assertEquals("paicoding-article-knowledge", saved.getTopic());
        assertEquals("article-knowledge-v1", saved.getTag());
        assertEquals("article:10", saved.getAggregateId());
        assertEquals(payload.getEventId(), saved.getEventId());

        saved.setId(9L);
        when(dao.listDispatchable(eq(100), any())).thenReturn(List.of(saved));
        when(dao.claim(eq(saved), any())).thenReturn(true);
        service.dispatch();

        verify(queueService).publish(eq("paicoding-article-knowledge"), eq("article-knowledge-v1"),
                any(ArticleKnowledgeEvent.class), eq(payload.getEventId()));
        verify(dao).markSent(9L);
    }

    private MqOutboxEventDO event(Long id, int retryCount) {
        MessageQueueEvent<String> payload = new MessageQueueEvent<>(NotifyTypeEnum.PRAISE, "payload", 7L);
        payload.setEventId("event-" + id);
        MqOutboxEventDO event = new MqOutboxEventDO();
        event.setId(id);
        event.setEventId(payload.getEventId());
        event.setTag("notify");
        event.setPayload(JsonUtil.toStr(payload));
        event.setRetryCount(retryCount);
        return event;
    }
}
