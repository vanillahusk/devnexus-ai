package com.github.paicoding.forum.aigc.mq;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeMetrics;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.enums.MqOutboxStatusEnum;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArticleKnowledgeMetricsTest {

    @Test
    void shouldExposeIndexOutboxResultAndLatencyWithoutIdentityLabels() {
        MqOutboxEventDao dao = new MqOutboxEventDao() {
            @Override
            public Map<Integer, Long> countByStatusForTag(String tag) {
                assertEquals("article-knowledge-v1", tag);
                return Map.of(MqOutboxStatusEnum.PENDING.getCode(), 3L,
                        MqOutboxStatusEnum.RETRY.getCode(), 2L,
                        MqOutboxStatusEnum.DEAD.getCode(), 1L);
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArticleKnowledgeMetrics metrics = new ArticleKnowledgeMetrics(dao, registry);
        metrics.refreshOutbox();
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                7L, 2L, ArticleKnowledgeOperationEnum.UPDATE);
        event.setOccurredAt(System.currentTimeMillis() - 20);
        Timer.Sample sample = metrics.start();
        metrics.success(sample, event, "applied");

        assertEquals(3D, registry.get("rag.index.outbox.events").tag("status", "pending").gauge().value());
        assertEquals(1D, registry.get("rag.index.events").tag("result", "applied").counter().count());
        assertEquals(1L, registry.get("rag.index.sync.latency").timer().count());
        assertEquals(1L, registry.get("rag.index.processing.duration")
                .tag("result", "applied").timer().count());
    }
}
