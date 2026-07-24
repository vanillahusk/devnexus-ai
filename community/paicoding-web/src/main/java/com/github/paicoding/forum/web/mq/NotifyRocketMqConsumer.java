package com.github.paicoding.forum.web.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.web.mq.comsumer.MessageQueueNotifyMsgConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paicoding.mq.provider", havingValue = "rocketmq")
@ConditionalOnExpression("'${message.service.mode:local}' == 'local'")
@RocketMQMessageListener(
        topic = "${paicoding.mq.rocketmq.topic:" + CommonConstants.ROCKETMQ_TOPIC_BUSINESS_EVENT + "}",
        selectorExpression = CommonConstants.ROCKETMQ_TAG_NOTIFY,
        consumerGroup = "${paicoding.mq.rocketmq.notify-consumer-group:paicoding-notify-group}",
        consumeThreadNumber = 10,
        maxReconsumeTimes = 5
)
public class NotifyRocketMqConsumer implements RocketMQListener<MessageQueueEvent<?>> {
    private final MessageQueueNotifyMsgConsumer notifyConsumer;
    private final MqConsumeIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageQueueEvent<?> event) {
        if (event == null || event.getNotifyType() == null || event.getContent() == null) {
            throw new IllegalArgumentException("notify rocketmq event/type/content must not be null");
        }
        event.validateSupportedVersion();
        String eventId = resolveEventId(event);
        MqConsumeIdempotencyService.ConsumeToken token = idempotencyService.tryAcquire("notify", eventId);
        if (token.state() == MqConsumeIdempotencyService.ConsumeState.COMPLETED) {
            log.info("skip duplicated RocketMQ notify event, eventId={}", eventId);
            return;
        }
        if (token.state() == MqConsumeIdempotencyService.ConsumeState.PROCESSING) {
            throw new IllegalStateException("notify rocketmq event is processing: " + eventId);
        }
        try {
            dispatch(event);
            idempotencyService.complete(token);
        } catch (RuntimeException e) {
            idempotencyService.release(token);
            throw e;
        }
    }

    private void dispatch(MessageQueueEvent<?> event) {
        switch (event.getNotifyType()) {
            case COMMENT -> notifyConsumer.saveCommentNotify(objectMapper.convertValue(event, new TypeReference<>() {}));
            case REPLY -> notifyConsumer.saveReplyNotify(objectMapper.convertValue(event, new TypeReference<>() {}));
            case PRAISE, COLLECT -> notifyConsumer.saveArticleNotify(objectMapper.convertValue(event, new TypeReference<>() {}));
            case CANCEL_PRAISE, CANCEL_COLLECT -> notifyConsumer.removeArticleNotify(objectMapper.convertValue(event, new TypeReference<>() {}));
            case FOLLOW -> notifyConsumer.saveFollowNotify(objectMapper.convertValue(event, new TypeReference<>() {}));
            case CANCEL_FOLLOW -> notifyConsumer.removeFollowNotify(objectMapper.convertValue(event, new TypeReference<>() {}));
            case REGISTER -> notifyConsumer.saveRegisterSystemNotify(event.getUserId());
            default -> throw new IllegalArgumentException("unsupported notify type: " + event.getNotifyType());
        }
    }

    private String resolveEventId(MessageQueueEvent<?> event) {
        return event.getEventId() == null || event.getEventId().isBlank()
                ? "legacy-" + Integer.toUnsignedString(event.toString().hashCode(), 16)
                : event.getEventId();
    }
}
