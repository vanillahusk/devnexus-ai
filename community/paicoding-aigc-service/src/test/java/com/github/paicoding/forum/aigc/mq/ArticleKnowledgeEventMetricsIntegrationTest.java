package com.github.paicoding.forum.aigc.mq;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeEventHandler;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexState;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexer;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeMetrics;
import com.github.paicoding.forum.service.ai.repository.dao.ArticleKnowledgeIndexStateDao;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleKnowledgeEventMetricsIntegrationTest {

    @Test
    void shouldRecordAppliedAndFailedHandlerResults() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CapturingStateDao state = new CapturingStateDao();
        ArticleKnowledgeMetrics metrics = new ArticleKnowledgeMetrics(emptyOutbox(), registry);
        ArticleKnowledgeIndexer successful = (articleId, version, operation) ->
                new ArticleKnowledgeIndexer.ApplyResult(version, operation);
        ArticleKnowledgeEventHandler handler = new ArticleKnowledgeEventHandler(state, successful, metrics);
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                7L, 2L, ArticleKnowledgeOperationEnum.UPDATE);

        assertEquals(ArticleKnowledgeEventHandler.HandleResult.APPLIED, handler.handle(event));
        assertEquals(event.getEventId(), state.applied.getEventId());
        assertEquals(1D, registry.get("rag.index.events").tag("result", "applied").counter().count());

        ArticleKnowledgeEventHandler failed = new ArticleKnowledgeEventHandler(new CapturingStateDao(),
                (articleId, version, operation) -> { throw new IllegalStateException("index unavailable"); },
                metrics);
        assertThrows(IllegalStateException.class, () -> failed.handle(
                ArticleKnowledgeEvent.create(8L, 1L, ArticleKnowledgeOperationEnum.ONLINE)));
        assertEquals(1D, registry.get("rag.index.events").tag("result", "failed").counter().count());
    }

    @Test
    void shouldIgnoreDuplicateAndLateEventWithoutCallingIndexer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CapturingStateDao state = new CapturingStateDao();
        ArticleKnowledgeMetrics metrics = new ArticleKnowledgeMetrics(emptyOutbox(), registry);
        ArticleKnowledgeEvent current = ArticleKnowledgeEvent.create(
                9L, 5L, ArticleKnowledgeOperationEnum.UPDATE);
        state.saveApplied(current);
        ArticleKnowledgeEventHandler handler = new ArticleKnowledgeEventHandler(state,
                (articleId, version, operation) -> {
                    throw new AssertionError("duplicate/stale event must not reach the indexer");
                }, metrics);

        ArticleKnowledgeEvent duplicate = ArticleKnowledgeEvent.create(
                9L, 5L, ArticleKnowledgeOperationEnum.UPDATE);
        ArticleKnowledgeEvent stale = ArticleKnowledgeEvent.create(
                9L, 4L, ArticleKnowledgeOperationEnum.ONLINE);

        assertEquals(ArticleKnowledgeEventHandler.HandleResult.DUPLICATE, handler.handle(duplicate));
        assertEquals(ArticleKnowledgeEventHandler.HandleResult.STALE, handler.handle(stale));
        assertEquals(current.getEventId(), state.applied.getEventId());
        assertEquals(1D, registry.get("rag.index.events").tag("result", "duplicate").counter().count());
        assertEquals(1D, registry.get("rag.index.events").tag("result", "stale").counter().count());
    }

    private MqOutboxEventDao emptyOutbox() {
        return new MqOutboxEventDao() {
            @Override
            public Map<Integer, Long> countByStatusForTag(String tag) {
                return Map.of();
            }
        };
    }

    private static final class CapturingStateDao extends ArticleKnowledgeIndexStateDao {
        private ArticleKnowledgeEvent applied;

        @Override
        public ArticleKnowledgeIndexState findState(Long articleId) {
            if (applied == null) return null;
            return new ArticleKnowledgeIndexState(applied.getArticleId(), applied.getArticleVersion(),
                    applied.getOperation(), applied.getEventId(), applied.getOccurredAt());
        }

        @Override
        public void saveApplied(ArticleKnowledgeEvent event) {
            applied = event;
        }
    }
}
