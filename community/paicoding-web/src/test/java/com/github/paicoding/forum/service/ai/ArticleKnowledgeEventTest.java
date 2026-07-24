package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleKnowledgeEventTest {

    @Test
    void shouldCreateValidV1EventAndStableBusinessKey() {
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                42L, 7L, ArticleKnowledgeOperationEnum.UPDATE);

        assertEquals(ArticleKnowledgeEvent.CURRENT_EVENT_VERSION, event.effectiveEventVersion());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
        assertEquals("42:7:UPDATE", event.idempotencyKey());
        assertEquals("42:7:UPDATE", event.idempotencyKey());
    }

    @Test
    void shouldTreatMissingContractVersionAsV1ForBackwardCompatibility() {
        ArticleKnowledgeEvent event = event("evt-1", 1L, 1L, ArticleKnowledgeOperationEnum.ONLINE);
        event.setEventVersion(null);

        event.validate();
        assertEquals(1, event.effectiveEventVersion());
    }

    @Test
    void shouldRejectUnsupportedVersionAndIncompleteMetadata() {
        ArticleKnowledgeEvent unsupported = event("evt-1", 1L, 1L, ArticleKnowledgeOperationEnum.ONLINE);
        unsupported.setEventVersion(2);
        assertThrows(IllegalArgumentException.class, unsupported::validate);

        ArticleKnowledgeEvent missingEventId = event(" ", 1L, 1L, ArticleKnowledgeOperationEnum.ONLINE);
        assertThrows(IllegalArgumentException.class, missingEventId::validate);

        ArticleKnowledgeEvent invalidVersion = event("evt-2", 1L, 0L, ArticleKnowledgeOperationEnum.UPDATE);
        assertThrows(IllegalArgumentException.class, invalidVersion::validate);
    }

    static ArticleKnowledgeEvent event(String eventId, Long articleId, Long articleVersion,
                                       ArticleKnowledgeOperationEnum operation) {
        ArticleKnowledgeEvent event = new ArticleKnowledgeEvent();
        event.setEventId(eventId);
        event.setArticleId(articleId);
        event.setArticleVersion(articleVersion);
        event.setOperation(operation);
        event.setOccurredAt(1000L);
        return event;
    }
}
