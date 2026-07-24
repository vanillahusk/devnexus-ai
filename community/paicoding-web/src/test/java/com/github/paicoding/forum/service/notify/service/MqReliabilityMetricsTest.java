package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqReliabilityMetricsTest {
    @Test
    void shouldExposeOutboxAndFavorQueueMetrics() {
        MqOutboxEventDao dao = mock(MqOutboxEventDao.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        when(dao.countByStatus()).thenReturn(Map.of(2, 3L, 4, 1L));
        when(redis.opsForList()).thenReturn(lists);
        when(lists.size(anyString())).thenReturn(5L);
        MqReliabilityMetrics metrics = new MqReliabilityMetrics(dao, redis, registry);

        metrics.refreshGauges();
        MqOutboxEventDO event = new MqOutboxEventDO();
        event.setCreateTime(new Date(System.currentTimeMillis() - 100L));
        metrics.recordSuccess(event);
        metrics.recordFailure(false);
        metrics.recordFailure(true);

        assertEquals(3D, registry.get("mq.outbox.events").tag("status", "retry").gauge().value());
        assertEquals(1D, registry.get("mq.outbox.events").tag("status", "dead").gauge().value());
        assertEquals(5D, registry.get("favor.queue.size").tag("queue", "pending").gauge().value());
        assertEquals(1D, registry.get("mq.outbox.dispatch").tag("result", "success").counter().count());
        assertEquals(1D, registry.get("mq.outbox.dispatch").tag("result", "retry").counter().count());
        assertEquals(1D, registry.get("mq.outbox.dispatch").tag("result", "dead").counter().count());
        assertEquals(1L, registry.get("mq.outbox.delivery.latency").timer().count());
        assertTrue(registry.get("mq.outbox.delivery.latency").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 0);

        String scrape = registry.scrape();
        assertTrue(scrape.contains("mq_outbox_events{status=\"retry\",} 3.0"));
        assertTrue(scrape.contains("mq_outbox_dispatch_total{result=\"dead\",} 1.0"));
        assertTrue(scrape.contains("mq_outbox_delivery_latency_seconds_count 1.0"));
        assertTrue(scrape.contains("favor_queue_size{queue=\"pending\",} 5.0"));
    }
}
