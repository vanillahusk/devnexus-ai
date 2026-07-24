package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeEventPolicy;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexState;
import org.junit.jupiter.api.Test;

import static com.github.paicoding.forum.service.ai.ArticleKnowledgeEventTest.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleKnowledgeEventPolicyTest {

    @Test
    void shouldAcceptFirstEventAndNewerArticleVersion() {
        ArticleKnowledgeEvent first = event("evt-1", 10L, 1L, ArticleKnowledgeOperationEnum.ONLINE);
        assertEquals(ArticleKnowledgeEventPolicy.Decision.ACCEPT,
                ArticleKnowledgeEventPolicy.evaluate(first, null));

        ArticleKnowledgeIndexState current = ArticleKnowledgeEventPolicy.apply(first, null);
        ArticleKnowledgeEvent newer = event("evt-2", 10L, 2L, ArticleKnowledgeOperationEnum.UPDATE);
        assertEquals(ArticleKnowledgeEventPolicy.Decision.ACCEPT,
                ArticleKnowledgeEventPolicy.evaluate(newer, current));
        assertEquals(2L, ArticleKnowledgeEventPolicy.apply(newer, current).articleVersion());
    }

    @Test
    void shouldRejectDuplicatePhysicalAndEquivalentBusinessEvents() {
        ArticleKnowledgeEvent applied = event("evt-1", 10L, 3L, ArticleKnowledgeOperationEnum.UPDATE);
        ArticleKnowledgeIndexState current = ArticleKnowledgeEventPolicy.apply(applied, null);

        assertEquals(ArticleKnowledgeEventPolicy.Decision.DUPLICATE,
                ArticleKnowledgeEventPolicy.evaluate(applied, current));

        ArticleKnowledgeEvent equivalent = event("evt-another", 10L, 3L, ArticleKnowledgeOperationEnum.UPDATE);
        assertEquals(ArticleKnowledgeEventPolicy.Decision.DUPLICATE,
                ArticleKnowledgeEventPolicy.evaluate(equivalent, current));
        assertSame(current, ArticleKnowledgeEventPolicy.apply(equivalent, current));
    }

    @Test
    void shouldRejectOlderVersionArrivingLate() {
        ArticleKnowledgeIndexState current = ArticleKnowledgeEventPolicy.apply(
                event("evt-new", 10L, 5L, ArticleKnowledgeOperationEnum.UPDATE), null);
        ArticleKnowledgeEvent stale = event("evt-old", 10L, 4L, ArticleKnowledgeOperationEnum.OFFLINE);

        assertEquals(ArticleKnowledgeEventPolicy.Decision.STALE,
                ArticleKnowledgeEventPolicy.evaluate(stale, current));
    }

    @Test
    void offlineShouldWinWithinSameVersionAndPreventResurrection() {
        ArticleKnowledgeIndexState online = ArticleKnowledgeEventPolicy.apply(
                event("evt-online", 10L, 8L, ArticleKnowledgeOperationEnum.ONLINE), null);
        ArticleKnowledgeEvent offlineEvent = event("evt-offline", 10L, 8L, ArticleKnowledgeOperationEnum.OFFLINE);

        assertEquals(ArticleKnowledgeEventPolicy.Decision.ACCEPT,
                ArticleKnowledgeEventPolicy.evaluate(offlineEvent, online));
        ArticleKnowledgeIndexState offline = ArticleKnowledgeEventPolicy.apply(offlineEvent, online);

        ArticleKnowledgeEvent lateUpdate = event("evt-update", 10L, 8L, ArticleKnowledgeOperationEnum.UPDATE);
        assertEquals(ArticleKnowledgeEventPolicy.Decision.STALE,
                ArticleKnowledgeEventPolicy.evaluate(lateUpdate, offline));
        assertSame(offline, ArticleKnowledgeEventPolicy.apply(lateUpdate, offline));
    }

    @Test
    void shouldNotCompareStatesFromDifferentArticles() {
        ArticleKnowledgeIndexState current = ArticleKnowledgeEventPolicy.apply(
                event("evt-1", 10L, 1L, ArticleKnowledgeOperationEnum.ONLINE), null);

        assertThrows(IllegalArgumentException.class, () -> ArticleKnowledgeEventPolicy.evaluate(
                event("evt-2", 11L, 2L, ArticleKnowledgeOperationEnum.UPDATE), current));
    }
}
