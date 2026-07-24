package com.github.paicoding.forum.aigc.mq;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeEventHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleKnowledgeConsumerTraceIntegrationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void restoresEventTraceDuringConsumptionAndThenRestoresCallerMdc() {
        AtomicReference<String> observed = new AtomicReference<>();
        ArticleKnowledgeEventHandler handler = new ArticleKnowledgeEventHandler(null, null, null) {
            @Override
            public HandleResult handle(ArticleKnowledgeEvent event) {
                observed.set(MDC.get("traceId"));
                return HandleResult.APPLIED;
            }
        };
        ArticleKnowledgeRocketMqConsumer consumer = new ArticleKnowledgeRocketMqConsumer(handler);
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                7L, 2L, ArticleKnowledgeOperationEnum.UPDATE);
        event.setTraceId("gateway-trace-12345678");
        MDC.put("traceId", "caller-trace-12345678");

        consumer.onMessage(event);

        assertEquals("gateway-trace-12345678", observed.get());
        assertEquals("caller-trace-12345678", MDC.get("traceId"));
    }

    @Test
    void replacesUnsafeTraceFromManuallyConstructedMessage() {
        AtomicReference<String> observed = new AtomicReference<>();
        ArticleKnowledgeEventHandler handler = new ArticleKnowledgeEventHandler(null, null, null) {
            @Override
            public HandleResult handle(ArticleKnowledgeEvent event) {
                observed.set(MDC.get("traceId"));
                return HandleResult.APPLIED;
            }
        };
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                8L, 1L, ArticleKnowledgeOperationEnum.ONLINE);
        event.setTraceId("bad trace\nforged-log-line");

        new ArticleKnowledgeRocketMqConsumer(handler).onMessage(event);

        assertNotEquals(event.getTraceId(), observed.get());
        assertTrue(observed.get().matches("[A-Za-z0-9._-]{8,64}"));
    }
}
