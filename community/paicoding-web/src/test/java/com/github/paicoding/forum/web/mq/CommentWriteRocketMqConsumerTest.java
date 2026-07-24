package com.github.paicoding.forum.web.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;
import com.github.paicoding.forum.service.comment.service.CommentWriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentWriteRocketMqConsumerTest {
    @Mock
    private CommentWriteService commentWriteService;

    @Test
    void shouldReturnOnlyAfterDatabaseWriteSucceeds() {
        CommentWriteRocketMqConsumer consumer =
                new CommentWriteRocketMqConsumer(commentWriteService, new ObjectMapper());
        MessageQueueEvent<CommentSaveReq> event = event();
        when(commentWriteService.saveCommentFromEvent(
                org.mockito.ArgumentMatchers.eq(event.getEventId()),
                org.mockito.ArgumentMatchers.any(CommentSaveReq.class))).thenReturn(101L);

        consumer.onMessage(event);

        ArgumentCaptor<CommentSaveReq> request = ArgumentCaptor.forClass(CommentSaveReq.class);
        verify(commentWriteService).saveCommentFromEvent(
                org.mockito.ArgumentMatchers.eq(event.getEventId()), request.capture());
        assertEquals("direct-to-mysql", request.getValue().getCommentContent());
    }

    @Test
    void shouldThrowSoRocketMqRetriesWhenDatabaseWriteFails() {
        CommentWriteRocketMqConsumer consumer =
                new CommentWriteRocketMqConsumer(commentWriteService, new ObjectMapper());
        MessageQueueEvent<CommentSaveReq> event = event();
        doThrow(new IllegalStateException("mysql unavailable")).when(commentWriteService)
                .saveCommentFromEvent(org.mockito.ArgumentMatchers.eq(event.getEventId()),
                        org.mockito.ArgumentMatchers.any(CommentSaveReq.class));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(event));
    }

    @Test
    void shouldRejectEventWithoutStableIdentity() {
        CommentWriteRocketMqConsumer consumer =
                new CommentWriteRocketMqConsumer(commentWriteService, new ObjectMapper());
        MessageQueueEvent<CommentSaveReq> event = event();
        event.setEventId(" ");

        assertThrows(IllegalArgumentException.class, () -> consumer.onMessage(event));
    }

    private MessageQueueEvent<CommentSaveReq> event() {
        CommentSaveReq request = new CommentSaveReq();
        request.setArticleId(14L);
        request.setUserId(7L);
        request.setCommentContent("direct-to-mysql");
        request.setParentCommentId(0L);
        request.setTopCommentId(0L);
        return new MessageQueueEvent<>(NotifyTypeEnum.COMMENT, request, 7L);
    }
}
