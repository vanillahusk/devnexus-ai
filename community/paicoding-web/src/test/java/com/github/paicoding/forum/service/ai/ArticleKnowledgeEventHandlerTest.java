package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeEventHandler;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexState;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexer;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeMetrics;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.github.paicoding.forum.service.ai.repository.dao.ArticleKnowledgeIndexStateDao;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.github.paicoding.forum.service.ai.ArticleKnowledgeEventTest.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleKnowledgeEventHandlerTest {
    private final ArticleKnowledgeIndexStateDao stateDao = mock(ArticleKnowledgeIndexStateDao.class);
    private final ArticleKnowledgeIndexer indexer = mock(ArticleKnowledgeIndexer.class);
    private final ArticleKnowledgeEventHandler handler = new ArticleKnowledgeEventHandler(stateDao, indexer,
            new ArticleKnowledgeMetrics(mock(MqOutboxEventDao.class), new SimpleMeterRegistry()));

    @Test
    void shouldUpsertOnlineAndPersistAppliedState() {
        ArticleKnowledgeEvent incoming = event("evt-1", 9L, 1L, ArticleKnowledgeOperationEnum.ONLINE);
        when(indexer.converge(9L, 1L, ArticleKnowledgeOperationEnum.ONLINE))
                .thenReturn(new ArticleKnowledgeIndexer.ApplyResult(1L, ArticleKnowledgeOperationEnum.ONLINE));

        assertEquals(ArticleKnowledgeEventHandler.HandleResult.APPLIED, handler.handle(incoming));

        verify(indexer).converge(9L, 1L, ArticleKnowledgeOperationEnum.ONLINE);
        verify(stateDao).saveApplied(incoming);
    }

    @Test
    void shouldRemoveOfflineArticleAndPersistAppliedState() {
        ArticleKnowledgeEvent incoming = event("evt-2", 9L, 3L, ArticleKnowledgeOperationEnum.OFFLINE);
        when(stateDao.findState(9L)).thenReturn(state(9L, 2L, ArticleKnowledgeOperationEnum.UPDATE, "evt-old"));
        when(indexer.converge(9L, 3L, ArticleKnowledgeOperationEnum.OFFLINE))
                .thenReturn(new ArticleKnowledgeIndexer.ApplyResult(3L, ArticleKnowledgeOperationEnum.OFFLINE));

        assertEquals(ArticleKnowledgeEventHandler.HandleResult.APPLIED, handler.handle(incoming));

        verify(indexer).converge(9L, 3L, ArticleKnowledgeOperationEnum.OFFLINE);
        verify(stateDao).saveApplied(incoming);
    }

    @Test
    void shouldPersistLatestFactVersionWhenOldEventConvergesToNewSnapshot() {
        ArticleKnowledgeEvent incoming = event("evt-old", 9L, 8L, ArticleKnowledgeOperationEnum.UPDATE);
        when(stateDao.findState(9L)).thenReturn(state(9L, 7L, ArticleKnowledgeOperationEnum.UPDATE, "evt-7"));
        when(indexer.converge(9L, 8L, ArticleKnowledgeOperationEnum.UPDATE))
                .thenReturn(new ArticleKnowledgeIndexer.ApplyResult(10L, ArticleKnowledgeOperationEnum.OFFLINE));

        assertEquals(ArticleKnowledgeEventHandler.HandleResult.APPLIED, handler.handle(incoming));

        ArgumentCaptor<ArticleKnowledgeEvent> applied = ArgumentCaptor.forClass(ArticleKnowledgeEvent.class);
        verify(stateDao).saveApplied(applied.capture());
        assertEquals(10L, applied.getValue().getArticleVersion());
        assertEquals(ArticleKnowledgeOperationEnum.OFFLINE, applied.getValue().getOperation());
        assertEquals("evt-old", applied.getValue().getEventId());
    }

    @Test
    void shouldSkipDuplicateAndStaleWithoutTouchingRemoteIndex() {
        ArticleKnowledgeIndexState current = state(9L, 5L, ArticleKnowledgeOperationEnum.OFFLINE, "evt-current");
        when(stateDao.findState(9L)).thenReturn(current);

        ArticleKnowledgeEvent duplicate = event("evt-current", 9L, 5L, ArticleKnowledgeOperationEnum.OFFLINE);
        assertEquals(ArticleKnowledgeEventHandler.HandleResult.DUPLICATE, handler.handle(duplicate));

        ArticleKnowledgeEvent stale = event("evt-stale", 9L, 4L, ArticleKnowledgeOperationEnum.UPDATE);
        assertEquals(ArticleKnowledgeEventHandler.HandleResult.STALE, handler.handle(stale));

        verify(indexer, never()).converge(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(stateDao, never()).saveApplied(org.mockito.ArgumentMatchers.any());
    }

    private ArticleKnowledgeIndexState state(Long articleId, Long version,
                                             ArticleKnowledgeOperationEnum operation, String eventId) {
        return new ArticleKnowledgeIndexState(articleId, version, operation, eventId, 1000L);
    }
}
