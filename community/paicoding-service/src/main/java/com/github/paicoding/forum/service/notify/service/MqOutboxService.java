package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.core.mdc.MdcUtil;
import com.github.paicoding.forum.core.mdc.SelfTraceIdGenerator;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.notify.repository.enums.MqOutboxStatusEnum;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqOutboxService {
    private static final int MAX_RETRIES = 10;
    private final MqOutboxEventDao outboxEventDao;
    private final MessageQueueService messageQueueService;
    private final MqReliabilityMetrics reliabilityMetrics;

    public void saveFavorNotify(String eventId, NotifyTypeEnum notifyType, UserFootDO foot) {
        MessageQueueEvent<UserFootDO> event = new MessageQueueEvent<>(notifyType, foot, foot.getUserId());
        event.setEventId(eventId);
        saveNotifyEvent(eventId, foot.getUserId() + ":" + foot.getDocumentId(), event);
    }

    public void saveCommentNotify(CommentDO comment, NotifyTypeEnum notifyType) {
        if (notifyType != NotifyTypeEnum.COMMENT && notifyType != NotifyTypeEnum.REPLY) {
            throw new IllegalArgumentException("comment notify type must be COMMENT or REPLY");
        }
        String eventId = "comment-notify:" + comment.getId();
        MessageQueueEvent<CommentDO> event = new MessageQueueEvent<>(notifyType, comment, comment.getUserId());
        event.setEventId(eventId);
        saveNotifyEvent(eventId, "comment:" + comment.getId(), event);
    }

    public void saveArticleKnowledge(ArticleKnowledgeEvent event) {
        if (event.getTraceId() == null || event.getTraceId().isBlank()) {
            String traceId = MdcUtil.getTraceId();
            event.setTraceId(traceId == null || traceId.isBlank()
                    ? SelfTraceIdGenerator.generate() : traceId);
        }
        event.validate();
        saveEvent(event.getEventId(), "article:" + event.getArticleId(),
                CommonConstants.ROCKETMQ_TOPIC_ARTICLE_KNOWLEDGE,
                CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1,
                JsonUtil.toStr(event));
    }

    private void saveNotifyEvent(String eventId, String aggregateId, MessageQueueEvent<?> event) {
        saveEvent(eventId, aggregateId, CommonConstants.ROCKETMQ_TOPIC_BUSINESS_EVENT,
                CommonConstants.ROCKETMQ_TAG_NOTIFY, JsonUtil.toStr(event));
    }

    private void saveEvent(String eventId, String aggregateId, String topic, String tag, String payload) {
        MqOutboxEventDO outbox = new MqOutboxEventDO();
        outbox.setEventId(eventId);
        outbox.setTopic(topic);
        outbox.setTag(tag);
        outbox.setAggregateId(aggregateId);
        outbox.setPayload(payload);
        outbox.setStatus(MqOutboxStatusEnum.PENDING.getCode());
        outbox.setRetryCount(0);
        outbox.setLastError("");
        try {
            outboxEventDao.save(outbox);
        } catch (DuplicateKeyException ignored) {
            log.info("skip duplicated outbox event, eventId={}", eventId);
        }
    }

    @Scheduled(fixedDelayString = "${paicoding.mq.outbox.flush-delay-ms:1000}")
    public void dispatch() {
        Date staleBefore = new Date(System.currentTimeMillis() - 300_000L);
        List<MqOutboxEventDO> events = outboxEventDao.listDispatchable(100, staleBefore);
        for (MqOutboxEventDO event : events) {
            if (!outboxEventDao.claim(event, staleBefore)) {
                continue;
            }
            dispatchOne(event);
        }
    }

    private void dispatchOne(MqOutboxEventDO outbox) {
        try {
            if (CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1.equals(outbox.getTag())) {
                ArticleKnowledgeEvent event = JsonUtil.toObj(outbox.getPayload(), ArticleKnowledgeEvent.class);
                event.validate();
                messageQueueService.publish(outbox.getTopic(), outbox.getTag(), event, event.getEventId());
            } else {
                MessageQueueEvent<?> event = JsonUtil.toObj(outbox.getPayload(), MessageQueueEvent.class);
                messageQueueService.publish(event, outbox.getTag());
            }
            outboxEventDao.markSent(outbox.getId());
            reliabilityMetrics.recordSuccess(outbox);
        } catch (Exception e) {
            int retries = outbox.getRetryCount() == null ? 1 : outbox.getRetryCount() + 1;
            int status = retries >= MAX_RETRIES
                    ? MqOutboxStatusEnum.DEAD.getCode() : MqOutboxStatusEnum.RETRY.getCode();
            long delaySeconds = Math.min(300L, 1L << Math.min(retries, 8));
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Date nextRetryTime = status == MqOutboxStatusEnum.DEAD.getCode()
                    ? null : new Date(System.currentTimeMillis() + delaySeconds * 1000L);
            outboxEventDao.markFailed(outbox.getId(), status, retries, nextRetryTime,
                    error.substring(0, Math.min(512, error.length())));
            reliabilityMetrics.recordFailure(status == MqOutboxStatusEnum.DEAD.getCode());
            log.warn("outbox dispatch failed, eventId={}, retryCount={}, status={}",
                    outbox.getEventId(), retries, status, e);
        }
    }

    public OutboxStatus status() {
        Map<Integer, Long> counts = outboxEventDao.countByStatus();
        Map<String, Long> namedCounts = new LinkedHashMap<>();
        namedCounts.put("pending", counts.getOrDefault(MqOutboxStatusEnum.PENDING.getCode(), 0L));
        namedCounts.put("sending", counts.getOrDefault(MqOutboxStatusEnum.SENDING.getCode(), 0L));
        namedCounts.put("retry", counts.getOrDefault(MqOutboxStatusEnum.RETRY.getCode(), 0L));
        namedCounts.put("sent", counts.getOrDefault(MqOutboxStatusEnum.SENT.getCode(), 0L));
        namedCounts.put("dead", counts.getOrDefault(MqOutboxStatusEnum.DEAD.getCode(), 0L));
        return new OutboxStatus(namedCounts);
    }

    public List<OutboxAbnormalEvent> abnormalEvents(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return outboxEventDao.listAbnormal(limit).stream().map(event -> new OutboxAbnormalEvent(
                event.getId(), event.getEventId(), event.getTag(), event.getAggregateId(), event.getStatus(),
                event.getRetryCount(), event.getNextRetryTime(), event.getLastError(), event.getUpdateTime()
        )).toList();
    }

    public boolean replayDead(Long id) {
        return outboxEventDao.replayDead(id);
    }

    public record OutboxStatus(Map<String, Long> counts) {
    }

    public record OutboxAbnormalEvent(Long id, String eventId, String tag, String aggregateId, Integer status,
                                      Integer retryCount, Date nextRetryTime, String lastError, Date updateTime) {
    }
}
