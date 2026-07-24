package com.github.paicoding.forum.message.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.service.notify.service.NotifyCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotifyRocketMqConsumerTest {

    @Mock
    private NotifyCommandService commandService;

    @Mock
    private MessageMqConsumeIdempotencyService idempotencyService;

    private NotifyRocketMqConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotifyRocketMqConsumer(commandService, idempotencyService, new ObjectMapper());
    }

    @Test
    void shouldDispatchAndCompleteAcquiredEvent() {
        MessageQueueEvent<Long> event = registerEvent();
        MessageMqConsumeIdempotencyService.ConsumeToken token = token(
                MessageMqConsumeIdempotencyService.ConsumeState.ACQUIRED);
        when(idempotencyService.tryAcquire(event.getEventId())).thenReturn(token);

        consumer.onMessage(event);

        verify(commandService).saveRegisterSystemNotify(7L);
        verify(idempotencyService).complete(token);
        verify(idempotencyService, never()).release(token);
    }

    @Test
    void shouldSkipCompletedDuplicate() {
        MessageQueueEvent<Long> event = registerEvent();
        MessageMqConsumeIdempotencyService.ConsumeToken token = token(
                MessageMqConsumeIdempotencyService.ConsumeState.COMPLETED);
        when(idempotencyService.tryAcquire(event.getEventId())).thenReturn(token);

        consumer.onMessage(event);

        verify(commandService, never()).saveRegisterSystemNotify(7L);
        verify(idempotencyService, never()).complete(token);
    }

    @Test
    void shouldRetryWhenSameEventIsStillProcessing() {
        MessageQueueEvent<Long> event = registerEvent();
        MessageMqConsumeIdempotencyService.ConsumeToken token = token(
                MessageMqConsumeIdempotencyService.ConsumeState.PROCESSING);
        when(idempotencyService.tryAcquire(event.getEventId())).thenReturn(token);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(event));

        verify(commandService, never()).saveRegisterSystemNotify(7L);
    }

    @Test
    void shouldReleaseIdempotencyTokenWhenBusinessWriteFails() {
        MessageQueueEvent<Long> event = registerEvent();
        MessageMqConsumeIdempotencyService.ConsumeToken token = token(
                MessageMqConsumeIdempotencyService.ConsumeState.ACQUIRED);
        when(idempotencyService.tryAcquire(event.getEventId())).thenReturn(token);
        doThrow(new IllegalStateException("database unavailable"))
                .when(commandService).saveRegisterSystemNotify(7L);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(event));

        verify(idempotencyService).release(token);
        verify(idempotencyService, never()).complete(token);
    }

    @Test
    void shouldRejectUnsupportedEventVersionBeforeAcquiringToken() {
        MessageQueueEvent<Long> event = registerEvent();
        event.setEventVersion(MessageQueueEvent.CURRENT_EVENT_VERSION + 1);

        assertThrows(IllegalArgumentException.class, () -> consumer.onMessage(event));

        verify(idempotencyService, never()).tryAcquire(event.getEventId());
    }

    private MessageQueueEvent<Long> registerEvent() {
        return new MessageQueueEvent<>(NotifyTypeEnum.REGISTER, 7L, 7L);
    }

    private MessageMqConsumeIdempotencyService.ConsumeToken token(
            MessageMqConsumeIdempotencyService.ConsumeState state) {
        return new MessageMqConsumeIdempotencyService.ConsumeToken("mq:consume:notify:test", state);
    }
}
