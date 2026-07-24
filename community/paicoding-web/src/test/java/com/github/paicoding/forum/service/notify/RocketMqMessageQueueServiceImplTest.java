package com.github.paicoding.forum.service.notify;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.service.notify.service.impl.RocketMqMessageQueueServiceImpl;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqMessageQueueServiceImplTest {
    @Test
    void shouldSendWithBusinessEventIdAsRocketMqKey() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        RocketMqMessageQueueServiceImpl service = new RocketMqMessageQueueServiceImpl(template);
        ReflectionTestUtils.setField(service, "topic", "business-topic");
        SendResult result = mock(SendResult.class);
        when(result.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        when(template.syncSend(eq("business-topic:notify"), captor.capture())).thenReturn(result);
        MessageQueueEvent<String> event = new MessageQueueEvent<>(NotifyTypeEnum.PRAISE, "payload");

        service.publish(event, "notify");

        assertEquals(event.getEventId(), captor.getValue().getHeaders().get(RocketMQHeaders.KEYS));
    }

    @Test
    void shouldFailFastWhenBrokerDoesNotConfirmSend() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        RocketMqMessageQueueServiceImpl service = new RocketMqMessageQueueServiceImpl(template);
        ReflectionTestUtils.setField(service, "topic", "business-topic");
        SendResult result = mock(SendResult.class);
        when(result.getSendStatus()).thenReturn(SendStatus.FLUSH_DISK_TIMEOUT);
        when(template.syncSend(eq("business-topic:notify"), any(Message.class))).thenReturn(result);

        assertThrows(IllegalStateException.class,
                () -> service.publish(new MessageQueueEvent<>(NotifyTypeEnum.PRAISE, "payload"), "notify"));
    }

    @Test
    void shouldSendIndependentContractToExplicitTopicAndTag() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        RocketMqMessageQueueServiceImpl service = new RocketMqMessageQueueServiceImpl(template);
        SendResult result = mock(SendResult.class);
        when(result.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        when(template.syncSendOrderly(eq("knowledge-topic:article-v1"), captor.capture(), eq("8")))
                .thenReturn(result);
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                8L, 2L, ArticleKnowledgeOperationEnum.UPDATE);

        service.publish("knowledge-topic", "article-v1", event, event.getEventId());

        assertEquals(event.getEventId(), captor.getValue().getHeaders().get(RocketMQHeaders.KEYS));
        assertEquals(event, captor.getValue().getPayload());
    }
}
