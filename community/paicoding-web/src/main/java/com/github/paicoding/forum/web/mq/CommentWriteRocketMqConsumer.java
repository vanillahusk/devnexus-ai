package com.github.paicoding.forum.web.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.service.comment.service.CommentWriteService;
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
        selectorExpression = CommonConstants.ROCKETMQ_TAG_COMMENT_WRITE,
        consumerGroup = "${paicoding.mq.rocketmq.comment-consumer-group:paicoding-comment-write-group}",
        consumeThreadNumber = 8,
        maxReconsumeTimes = 5
)
public class CommentWriteRocketMqConsumer implements RocketMQListener<MessageQueueEvent<?>> {
    private final CommentWriteService commentWriteService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageQueueEvent<?> event) {
        if (event == null || event.getContent() == null) {
            throw new IllegalArgumentException("comment rocketmq event/content must not be null");
        }
        event.validateSupportedVersion();
        String eventId = resolveEventId(event);
        CommentSaveReq request = objectMapper.convertValue(event.getContent(), CommentSaveReq.class);
        Long commentId = commentWriteService.saveCommentFromEvent(eventId, request);
        log.info("RocketMQ comment event persisted, eventId={}, commentId={}", eventId, commentId);
    }

    private String resolveEventId(MessageQueueEvent<?> event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("comment rocketmq eventId must not be blank");
        }
        return event.getEventId();
    }
}
