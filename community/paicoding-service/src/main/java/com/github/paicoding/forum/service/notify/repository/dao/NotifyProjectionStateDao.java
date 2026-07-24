package com.github.paicoding.forum.service.notify.repository.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.paicoding.forum.service.notify.repository.entity.NotifyProjectionStateDO;
import com.github.paicoding.forum.service.notify.repository.mapper.NotifyProjectionStateMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public class NotifyProjectionStateDao
        extends ServiceImpl<NotifyProjectionStateMapper, NotifyProjectionStateDO> {

    public NotifyProjectionStateDO lockOrCreate(String aggregateKey) {
        getBaseMapper().insertIfAbsent(aggregateKey);
        return lambdaQuery().eq(NotifyProjectionStateDO::getAggregateKey, aggregateKey)
                .last("FOR UPDATE")
                .one();
    }

    public boolean advance(Long id, Long currentVersion, Long nextVersion, boolean desiredState) {
        return lambdaUpdate().eq(NotifyProjectionStateDO::getId, id)
                .eq(NotifyProjectionStateDO::getBusinessVersion, currentVersion)
                .lt(NotifyProjectionStateDO::getBusinessVersion, nextVersion)
                .set(NotifyProjectionStateDO::getBusinessVersion, nextVersion)
                .set(NotifyProjectionStateDO::getDesiredState, desiredState ? 1 : 0)
                .set(NotifyProjectionStateDO::getUpdateTime, new Date())
                .update();
    }
}
