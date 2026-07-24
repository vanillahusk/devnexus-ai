package com.github.paicoding.forum.service.notify.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.paicoding.forum.api.model.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mq_outbox_event")
public class MqOutboxEventDO extends BaseDO {
    private String eventId;
    private String topic;
    private String tag;
    private String aggregateId;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryTime;
    private String lastError;
}
