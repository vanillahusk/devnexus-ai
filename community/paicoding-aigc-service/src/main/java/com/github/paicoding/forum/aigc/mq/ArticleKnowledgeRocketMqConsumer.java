package com.github.paicoding.forum.aigc.mq;

import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.core.mdc.MdcUtil;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Map;
import org.slf4j.MDC;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${paicoding.mq.provider:none}' == 'rocketmq' "
        + "&& '${ai.knowledge.ragent.enabled:false}' == 'true'")
@RocketMQMessageListener(
        topic = "${paicoding.mq.rocketmq.article-knowledge-topic:" + CommonConstants.ROCKETMQ_TOPIC_ARTICLE_KNOWLEDGE + "}",
        selectorExpression = CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1,
        consumerGroup = "${paicoding.mq.rocketmq.article-knowledge-consumer-group:paicoding-aigc-article-knowledge-v1}",
        consumeMode = ConsumeMode.ORDERLY,
        consumeThreadNumber = 2,
        maxReconsumeTimes = 10
)
public class ArticleKnowledgeRocketMqConsumer implements RocketMQListener<ArticleKnowledgeEvent> {
    private final ArticleKnowledgeEventHandler handler;

    @Override
    @Trace(operationName = "rag.index.rocketmq.consume")
    public void onMessage(ArticleKnowledgeEvent event) {
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        if (event != null && event.getTraceId() != null && !event.getTraceId().isBlank()) {
            MdcUtil.setTraceId(event.getTraceId());
        } else {
            MdcUtil.addTraceId();
        }
        try {
            ArticleKnowledgeEventHandler.HandleResult result = handler.handle(event);
            log.info("article knowledge event consumed, eventId={}, articleId={}, version={}, result={}",
                    event.getEventId(), event.getArticleId(), event.getArticleVersion(), result);
        } catch (RuntimeException failure) {
            log.warn("article knowledge event failed, eventId={}, articleId={}, version={}, result=FAILED, cause={}",
                    event == null ? null : event.getEventId(),
                    event == null ? null : event.getArticleId(),
                    event == null ? null : event.getArticleVersion(),
                    failure.getClass().getSimpleName());
            throw failure;
        } finally {
            MDC.clear();
            if (previousMdc != null) {
                MDC.setContextMap(previousMdc);
            }
        }
    }
}
