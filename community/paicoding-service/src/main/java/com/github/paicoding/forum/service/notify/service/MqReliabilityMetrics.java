package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.notify.repository.enums.MqOutboxStatusEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MqReliabilityMetrics {
    private static final Map<String, String> FAVOR_QUEUE_KEYS = favorQueueKeys();

    private final MqOutboxEventDao outboxEventDao;
    private final StringRedisTemplate redisTemplate;
    private final Map<MqOutboxStatusEnum, AtomicLong> outboxGauges = new EnumMap<>(MqOutboxStatusEnum.class);
    private final Map<String, AtomicLong> favorQueueGauges = new LinkedHashMap<>();
    private final Counter dispatchSuccess;
    private final Counter dispatchRetry;
    private final Counter dispatchDead;
    private final Timer deliveryLatency;

    public MqReliabilityMetrics(MqOutboxEventDao outboxEventDao, StringRedisTemplate redisTemplate,
                                MeterRegistry registry) {
        this.outboxEventDao = outboxEventDao;
        this.redisTemplate = redisTemplate;
        for (MqOutboxStatusEnum status : MqOutboxStatusEnum.values()) {
            AtomicLong value = new AtomicLong();
            outboxGauges.put(status, value);
            Gauge.builder("mq.outbox.events", value, AtomicLong::get)
                    .tag("status", status.name().toLowerCase()).register(registry);
        }
        FAVOR_QUEUE_KEYS.forEach((name, key) -> {
            AtomicLong value = new AtomicLong();
            favorQueueGauges.put(name, value);
            Gauge.builder("favor.queue.size", value, AtomicLong::get)
                    .tag("queue", name).register(registry);
        });
        dispatchSuccess = counter(registry, "success");
        dispatchRetry = counter(registry, "retry");
        dispatchDead = counter(registry, "dead");
        deliveryLatency = Timer.builder("mq.outbox.delivery.latency")
                .description("Time from outbox creation to successful RocketMQ publish")
                .publishPercentileHistogram().register(registry);
    }

    @Scheduled(fixedDelayString = "${paicoding.mq.metrics.refresh-ms:5000}")
    public void refreshGauges() {
        Map<Integer, Long> counts = outboxEventDao.countByStatus();
        outboxGauges.forEach((status, value) -> value.set(counts.getOrDefault(status.getCode(), 0L)));
        FAVOR_QUEUE_KEYS.forEach((name, key) -> {
            Long size = redisTemplate.opsForList().size(key);
            favorQueueGauges.get(name).set(size == null ? 0L : size);
        });
    }

    public void recordSuccess(MqOutboxEventDO event) {
        dispatchSuccess.increment();
        if (event.getCreateTime() != null) {
            long millis = Math.max(0L, System.currentTimeMillis() - event.getCreateTime().getTime());
            deliveryLatency.record(Duration.ofMillis(millis));
        }
    }

    public void recordFailure(boolean dead) {
        (dead ? dispatchDead : dispatchRetry).increment();
    }

    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("mq.outbox.dispatch")
                .description("Outbox publish attempts by result")
                .tag("result", result).register(registry);
    }

    private static Map<String, String> favorQueueKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("pending", "favor:event:queue");
        keys.put("processing", "favor:event:processing:queue");
        keys.put("persist_retry", "favor:persist:retry:queue");
        keys.put("persist_retry_processing", "favor:persist:retry:processing:queue");
        keys.put("persist_dead", "favor:persist:dead:queue");
        return keys;
    }

}
