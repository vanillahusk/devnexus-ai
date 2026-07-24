package com.github.paicoding.forum.service.ai.index;

import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.enums.MqOutboxStatusEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 文章知识索引低基数指标；事件和文章身份只进入 Trace/脱敏日志。 */
@Component
public class ArticleKnowledgeMetrics {

    private static final MqOutboxStatusEnum[] OBSERVED = {
            MqOutboxStatusEnum.PENDING, MqOutboxStatusEnum.RETRY, MqOutboxStatusEnum.DEAD
    };
    private final MqOutboxEventDao outboxEventDao;
    private final MeterRegistry registry;
    private final Map<MqOutboxStatusEnum, AtomicLong> outbox = new EnumMap<>(MqOutboxStatusEnum.class);

    public ArticleKnowledgeMetrics(MqOutboxEventDao outboxEventDao, MeterRegistry registry) {
        this.outboxEventDao = outboxEventDao;
        this.registry = registry;
        for (MqOutboxStatusEnum status : OBSERVED) {
            AtomicLong value = new AtomicLong();
            outbox.put(status, value);
            Gauge.builder("rag.index.outbox.events", value, AtomicLong::get)
                    .tag("status", status.name().toLowerCase(java.util.Locale.ROOT))
                    .register(registry);
        }
    }

    @Scheduled(fixedDelayString = "${ai.knowledge.metrics.refresh-ms:15000}")
    public void refreshOutbox() {
        Map<Integer, Long> counts = outboxEventDao.countByStatusForTag(
                CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1);
        outbox.forEach((status, value) -> value.set(counts.getOrDefault(status.getCode(), 0L)));
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void success(Timer.Sample sample, ArticleKnowledgeEvent event, String result) {
        String safeResult = safeResult(result);
        stop(sample, safeResult);
        Counter.builder("rag.index.events")
                .tag("result", safeResult)
                .register(registry)
                .increment();
        if ("applied".equals(safeResult)
                && event != null && event.getOccurredAt() != null) {
            long millis = Math.max(0L, System.currentTimeMillis() - event.getOccurredAt());
            Timer.builder("rag.index.sync.latency")
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Duration.ofMillis(millis));
        }
    }

    public void failure(Timer.Sample sample) {
        stop(sample, "failed");
        Counter.builder("rag.index.events").tag("result", "failed")
                .register(registry).increment();
    }

    private void stop(Timer.Sample sample, String result) {
        sample.stop(Timer.builder("rag.index.processing.duration")
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry));
    }

    private String safeResult(String result) {
        return switch (result) {
            case "applied", "duplicate", "stale", "failed" -> result;
            default -> "failed";
        };
    }
}
