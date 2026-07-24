package com.github.paicoding.forum.service.notify.repository.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.notify.repository.mapper.MqOutboxEventMapper;
import com.github.paicoding.forum.service.notify.repository.enums.MqOutboxStatusEnum;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.github.paicoding.forum.core.common.CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1;

@Repository
public class MqOutboxEventDao extends ServiceImpl<MqOutboxEventMapper, MqOutboxEventDO> {
    public long latestArticleKnowledgeWatermark() {
        MqOutboxEventDO latest = lambdaQuery()
                .eq(MqOutboxEventDO::getTag, ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1)
                .orderByDesc(MqOutboxEventDO::getId)
                .last("LIMIT 1")
                .one();
        return latest == null || latest.getId() == null ? 0L : latest.getId();
    }

    public List<MqOutboxEventDO> listArticleKnowledgeBetween(long afterExclusive, long upperInclusive, int limit) {
        return lambdaQuery()
                .eq(MqOutboxEventDO::getTag, ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1)
                .gt(MqOutboxEventDO::getId, afterExclusive)
                .le(MqOutboxEventDO::getId, upperInclusive)
                .orderByAsc(MqOutboxEventDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500)))
                .list();
    }
    public List<MqOutboxEventDO> listDispatchable(int limit, Date staleBefore) {
        return lambdaQuery()
                .and(q -> q.in(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.PENDING.getCode(), MqOutboxStatusEnum.RETRY.getCode())
                        .and(t -> t.isNull(MqOutboxEventDO::getNextRetryTime)
                                .or().le(MqOutboxEventDO::getNextRetryTime, new Date()))
                        .or(s -> s.eq(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.SENDING.getCode())
                                .le(MqOutboxEventDO::getUpdateTime, staleBefore)))
                .orderByAsc(MqOutboxEventDO::getId)
                .last("LIMIT " + limit)
                .list();
    }

    public boolean claim(MqOutboxEventDO event, Date staleBefore) {
        return lambdaUpdate()
                .eq(MqOutboxEventDO::getId, event.getId())
                .and(q -> q.in(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.PENDING.getCode(), MqOutboxStatusEnum.RETRY.getCode())
                        .or(s -> s.eq(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.SENDING.getCode())
                                .le(MqOutboxEventDO::getUpdateTime, staleBefore)))
                .set(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.SENDING.getCode())
                .set(MqOutboxEventDO::getUpdateTime, new Date())
                .update();
    }

    public void markSent(Long id) {
        lambdaUpdate().eq(MqOutboxEventDO::getId, id)
                .set(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.SENT.getCode())
                .set(MqOutboxEventDO::getNextRetryTime, null)
                .set(MqOutboxEventDO::getLastError, "")
                .update();
    }

    public void markFailed(Long id, int status, int retryCount, Date nextRetryTime, String error) {
        lambdaUpdate().eq(MqOutboxEventDO::getId, id)
                .set(MqOutboxEventDO::getStatus, status)
                .set(MqOutboxEventDO::getRetryCount, retryCount)
                .set(MqOutboxEventDO::getNextRetryTime, nextRetryTime)
                .set(MqOutboxEventDO::getLastError, error)
                .update();
    }

    public Map<Integer, Long> countByStatus() {
        Map<Integer, Long> result = new java.util.HashMap<>();
        getBaseMapper().selectMaps(new QueryWrapper<MqOutboxEventDO>()
                        .select("status", "COUNT(*) AS event_count")
                        .groupBy("status"))
                .forEach(row -> result.put(((Number) row.get("status")).intValue(),
                        ((Number) row.get("event_count")).longValue()));
        return result;
    }

    public Map<Integer, Long> countByStatusForTag(String tag) {
        Map<Integer, Long> result = new java.util.HashMap<>();
        getBaseMapper().selectMaps(new QueryWrapper<MqOutboxEventDO>()
                        .select("status", "COUNT(*) AS event_count")
                        .eq("tag", tag)
                        .groupBy("status"))
                .forEach(row -> result.put(((Number) row.get("status")).intValue(),
                        ((Number) row.get("event_count")).longValue()));
        return result;
    }

    public List<MqOutboxEventDO> listAbnormal(int limit) {
        return lambdaQuery().in(MqOutboxEventDO::getStatus,
                        MqOutboxStatusEnum.RETRY.getCode(), MqOutboxStatusEnum.DEAD.getCode())
                .orderByDesc(MqOutboxEventDO::getUpdateTime)
                .last("LIMIT " + limit)
                .list();
    }

    public boolean replayDead(Long id) {
        return lambdaUpdate().eq(MqOutboxEventDO::getId, id)
                .eq(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.DEAD.getCode())
                .set(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.RETRY.getCode())
                .set(MqOutboxEventDO::getRetryCount, 0)
                .set(MqOutboxEventDO::getNextRetryTime, new Date())
                .set(MqOutboxEventDO::getLastError, "manual replay")
                .update();
    }
}
