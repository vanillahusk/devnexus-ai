package com.github.paicoding.forum.service.notify.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.paicoding.forum.service.notify.repository.entity.NotifyProjectionStateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface NotifyProjectionStateMapper extends BaseMapper<NotifyProjectionStateDO> {
    @Insert("INSERT IGNORE INTO notify_projection_state "
            + "(aggregate_key, business_version, desired_state, create_time, update_time) "
            + "VALUES (#{aggregateKey}, 0, 0, NOW(), NOW())")
    int insertIfAbsent(@Param("aggregateKey") String aggregateKey);
}
