package com.github.paicoding.forum.service.notify.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.paicoding.forum.api.model.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mq_dlq_replay_audit")
public class DlqReplayAuditDO extends BaseDO {
    private String originalMsgId;
    private String originalEventId;
    private String correctionEventId;
    private String topic;
    private String tag;
    private String businessKey;
    private String reason;
    private Long operatorId;
    private String status;
    private String errorSummary;
}
