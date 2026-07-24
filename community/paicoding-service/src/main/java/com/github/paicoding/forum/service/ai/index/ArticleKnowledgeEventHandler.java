package com.github.paicoding.forum.service.ai.index;

import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.repository.dao.ArticleKnowledgeIndexStateDao;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleKnowledgeEventHandler {
    private final ArticleKnowledgeIndexStateDao stateDao;
    private final ArticleKnowledgeIndexer indexer;
    private final ArticleKnowledgeMetrics metrics;

    @Transactional
    @Trace(operationName = "rag.index.converge")
    public HandleResult handle(ArticleKnowledgeEvent event) {
        io.micrometer.core.instrument.Timer.Sample sample = metrics.start();
        try {
            event.validate();
            ArticleKnowledgeIndexState current = stateDao.findState(event.getArticleId());
            ArticleKnowledgeEventPolicy.Decision decision = ArticleKnowledgeEventPolicy.evaluate(event, current);
            if (decision != ArticleKnowledgeEventPolicy.Decision.ACCEPT) {
                HandleResult skipped = decision == ArticleKnowledgeEventPolicy.Decision.DUPLICATE
                        ? HandleResult.DUPLICATE : HandleResult.STALE;
                metrics.success(sample, event, skipped.name().toLowerCase(java.util.Locale.ROOT));
                return skipped;
            }

            ArticleKnowledgeIndexer.ApplyResult applied = indexer.converge(
                    event.getArticleId(), event.getArticleVersion(), event.getOperation());
            if (applied.articleVersion() < event.getArticleVersion()) {
                throw new IllegalStateException("article fact version is behind event, articleId="
                        + event.getArticleId() + ", eventVersion=" + event.getArticleVersion()
                        + ", factVersion=" + applied.articleVersion());
            }
            stateDao.saveApplied(appliedEvent(event, applied));
            metrics.success(sample, event, "applied");
            return HandleResult.APPLIED;
        } catch (RuntimeException failure) {
            metrics.failure(sample);
            throw failure;
        }
    }

    private ArticleKnowledgeEvent appliedEvent(ArticleKnowledgeEvent source,
                                                ArticleKnowledgeIndexer.ApplyResult applied) {
        if (source.getArticleVersion().equals(applied.articleVersion())
                && source.getOperation() == applied.operation()) {
            return source;
        }
        ArticleKnowledgeEvent normalized = new ArticleKnowledgeEvent();
        normalized.setEventVersion(source.getEventVersion());
        normalized.setEventId(source.getEventId());
        normalized.setOriginalEventId(source.getOriginalEventId());
        normalized.setTraceId(source.getTraceId());
        normalized.setArticleId(source.getArticleId());
        normalized.setArticleVersion(applied.articleVersion());
        normalized.setOperation(applied.operation());
        normalized.setOccurredAt(source.getOccurredAt());
        normalized.validate();
        return normalized;
    }

    public enum HandleResult {
        APPLIED,
        DUPLICATE,
        STALE
    }
}
