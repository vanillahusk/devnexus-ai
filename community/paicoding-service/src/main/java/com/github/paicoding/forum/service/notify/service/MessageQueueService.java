package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.api.model.event.MessageQueueEvent;

public interface MessageQueueService {
    boolean enabled();

    <T> void publish(MessageQueueEvent<T> event, String tag);

    /** 向指定 Topic/Tag 发送独立业务契约，eventId 同时作为 RocketMQ Key。 */
    void publish(String topic, String tag, Object event, String eventId);
}
