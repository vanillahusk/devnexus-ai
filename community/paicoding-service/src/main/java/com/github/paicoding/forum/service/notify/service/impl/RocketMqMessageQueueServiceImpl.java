package com.github.paicoding.forum.service.notify.service.impl;

import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paicoding.mq.provider", havingValue = "rocketmq")
public class RocketMqMessageQueueServiceImpl implements MessageQueueService {
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${paicoding.mq.rocketmq.topic:" + CommonConstants.ROCKETMQ_TOPIC_BUSINESS_EVENT + "}")
    private String topic;

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public <T> void publish(MessageQueueEvent<T> event, String tag) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("rocketmq event/eventId must not be null");
        }
        publish(topic, tag, event, event.getEventId());
    }

    @Override
    public void publish(String targetTopic, String tag, Object event, String eventId) {
        if (targetTopic == null || targetTopic.isBlank() || tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("rocketmq topic/tag must not be blank");
        }
        if (event == null || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("rocketmq event/eventId must not be null");
        }
        org.springframework.messaging.Message<Object> message = MessageBuilder.withPayload(event)
                .setHeader(RocketMQHeaders.KEYS, eventId)
                .build();
        SendResult result;
        if (event instanceof ArticleKnowledgeEvent knowledgeEvent) {
            result = rocketMQTemplate.syncSendOrderly(targetTopic + ':' + tag, message,
                    String.valueOf(knowledgeEvent.getArticleId()));
        } else {
            result = rocketMQTemplate.syncSend(targetTopic + ':' + tag, message);
        }
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("rocketmq send failed, eventId=" + eventId
                    + ", result=" + result);
        }
    }
}
