package com.github.paicoding.forum.api.model.event;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import lombok.*;

import java.util.UUID;

/**
 * @program: pai_coding
 * @description: mq生产和消费的事件
 * @author: XuYifei
 * @create: 2024-10-31
 */

@Getter
@Setter
@ToString
public class MessageQueueEvent<T> {
    public static final int CURRENT_EVENT_VERSION = 1;

    /** 消息契约版本；历史消息缺失该字段时按 V1 处理。 */
    private Integer eventVersion = CURRENT_EVENT_VERSION;

    /** 业务事件唯一标识，用于消费者幂等；兼容没有该字段的历史消息。 */
    private String eventId;

    /** 修正事件关联的原始事件 ID；普通事件和原样重试为空。 */
    private String originalEventId;

    private Long occurredAt;

    private NotifyTypeEnum notifyType;

    private T content;

    private Long userId;

    public MessageQueueEvent() {
        // Jackson 反序列化历史消息时保留 null，由消费者生成兼容键。
    }

    public MessageQueueEvent(NotifyTypeEnum notifyType, T content) {
        initEventMetadata();
        this.notifyType = notifyType;
        this.content = content;
    }

    public MessageQueueEvent(NotifyTypeEnum notifyType, T content, Long userId) {
        initEventMetadata();
        this.notifyType = notifyType;
        this.content = content;
        this.userId = userId;
    }

    public int effectiveEventVersion() {
        return eventVersion == null ? CURRENT_EVENT_VERSION : eventVersion;
    }

    public void validateSupportedVersion() {
        if (effectiveEventVersion() != CURRENT_EVENT_VERSION) {
            throw new IllegalArgumentException("unsupported mq event version: " + eventVersion);
        }
    }

    private void initEventMetadata() {
        this.eventVersion = CURRENT_EVENT_VERSION;
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = System.currentTimeMillis();
    }
}
