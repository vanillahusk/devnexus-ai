package com.github.paicoding.forum.aigc.mq;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeEventHandler;
import org.junit.jupiter.api.Test;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleKnowledgeRocketMqConsumerTest {
    @Test
    void shouldDelegateEventToReliableHandler() {
        ArticleKnowledgeEventHandler handler = mock(ArticleKnowledgeEventHandler.class);
        ArticleKnowledgeRocketMqConsumer consumer = new ArticleKnowledgeRocketMqConsumer(handler);
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                7L, 2L, ArticleKnowledgeOperationEnum.UPDATE);

        consumer.onMessage(event);

        verify(handler).handle(event);
    }

    @Test
    void shouldUseIndependentOrderedConsumerWithBoundedRetries() {
        RocketMQMessageListener config = ArticleKnowledgeRocketMqConsumer.class
                .getAnnotation(RocketMQMessageListener.class);

        assertEquals("${paicoding.mq.rocketmq.article-knowledge-topic:paicoding-article-knowledge}",
                config.topic());
        assertEquals("article-knowledge-v1", config.selectorExpression());
        assertEquals("${paicoding.mq.rocketmq.article-knowledge-consumer-group:paicoding-aigc-article-knowledge-v1}",
                config.consumerGroup());
        assertEquals(ConsumeMode.ORDERLY, config.consumeMode());
        assertEquals(10, config.maxReconsumeTimes());
    }

    @Test
    void shouldRethrowHandlerFailureSoRocketMqCanRetry() {
        ArticleKnowledgeEventHandler handler = mock(ArticleKnowledgeEventHandler.class);
        ArticleKnowledgeRocketMqConsumer consumer = new ArticleKnowledgeRocketMqConsumer(handler);
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                7L, 3L, ArticleKnowledgeOperationEnum.ONLINE);
        when(handler.handle(event)).thenThrow(new IllegalStateException("fact version lag"));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(event));
        verify(handler).handle(event);
    }
}
