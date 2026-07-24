package com.github.paicoding.forum.service.notify.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.paicoding.forum.api.model.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_projection_state")
public class NotifyProjectionStateDO extends BaseDO {
    private String aggregateKey;
    private Long businessVersion;
    private Integer desiredState;
}
