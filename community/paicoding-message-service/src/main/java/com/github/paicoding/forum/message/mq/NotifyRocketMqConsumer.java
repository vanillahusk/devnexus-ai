package com.github.paicoding.forum.message.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.service.NotifyCommandService;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paicoding.mq.provider", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "${paicoding.mq.rocketmq.topic:" + CommonConstants.ROCKETMQ_TOPIC_BUSINESS_EVENT + "}",
        selectorExpression = CommonConstants.ROCKETMQ_TAG_NOTIFY,
        consumerGroup = "${paicoding.mq.rocketmq.notify-consumer-group:paicoding-notify-group}",
        consumeThreadNumber = 10,
        maxReconsumeTimes = 5
)
public class NotifyRocketMqConsumer implements RocketMQListener<MessageQueueEvent<?>> {

    private final NotifyCommandService commandService;
    private final MessageMqConsumeIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageQueueEvent<?> event) {
        if (event == null || event.getNotifyType() == null || event.getContent() == null) {
            throw new IllegalArgumentException("notify rocketmq event/type/content must not be null");
        }
        event.validateSupportedVersion();
        String eventId = resolveEventId(event);
        MessageMqConsumeIdempotencyService.ConsumeToken token = idempotencyService.tryAcquire(eventId);
        if (token.state() == MessageMqConsumeIdempotencyService.ConsumeState.COMPLETED) {
            log.info("skip duplicated RocketMQ notify event, eventId={}", eventId);
            return;
        }
        if (token.state() == MessageMqConsumeIdempotencyService.ConsumeState.PROCESSING) {
            throw new IllegalStateException("notify rocketmq event is processing: " + eventId);
        }
        try {
            dispatch(event);
            idempotencyService.complete(token);
        } catch (RuntimeException ex) {
            idempotencyService.release(token);
            throw ex;
        }
    }

    private void dispatch(MessageQueueEvent<?> event) {
        switch (event.getNotifyType()) {
            case COMMENT -> commandService.saveCommentNotify(convert(event));
            case REPLY -> commandService.saveReplyNotify(convert(event));
            case PRAISE, COLLECT -> commandService.saveArticleNotify(convert(event));
            case CANCEL_PRAISE, CANCEL_COLLECT -> commandService.removeArticleNotify(convert(event));
            case FOLLOW -> commandService.saveFollowNotify(convert(event));
            case CANCEL_FOLLOW -> commandService.removeFollowNotify(convert(event));
            case REGISTER -> commandService.saveRegisterSystemNotify(event.getUserId());
            default -> throw new IllegalArgumentException("unsupported notify type: " + event.getNotifyType());
        }
    }

    private <T> MessageQueueEvent<T> convert(MessageQueueEvent<?> event) {
        return objectMapper.convertValue(event, new TypeReference<>() { });
    }

    private String resolveEventId(MessageQueueEvent<?> event) {
        return event.getEventId() == null || event.getEventId().isBlank()
                ? "legacy-" + Integer.toUnsignedString(event.toString().hashCode(), 16)
                : event.getEventId();
    }
}
