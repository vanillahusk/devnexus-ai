package com.github.paicoding.forum.service.ai.index;

import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;

/**
 * 与存储实现无关的索引事件幂等、版本和乱序规则。
 */
public final class ArticleKnowledgeEventPolicy {
    private ArticleKnowledgeEventPolicy() {
    }

    public static Decision evaluate(ArticleKnowledgeEvent incoming, ArticleKnowledgeIndexState current) {
        incoming.validate();
        if (current == null) {
            return Decision.ACCEPT;
        }
        validateCurrentState(incoming, current);
        if (incoming.getEventId().equals(current.eventId())) {
            return Decision.DUPLICATE;
        }

        int versionComparison = incoming.getArticleVersion().compareTo(current.articleVersion());
        if (versionComparison > 0) {
            return Decision.ACCEPT;
        }
        if (versionComparison < 0) {
            return Decision.STALE;
        }

        int operationComparison = Integer.compare(
                incoming.getOperation().precedence(), current.operation().precedence());
        if (operationComparison > 0) {
            return Decision.ACCEPT;
        }
        if (operationComparison < 0) {
            return Decision.STALE;
        }
        return Decision.DUPLICATE;
    }

    public static ArticleKnowledgeIndexState apply(ArticleKnowledgeEvent incoming,
                                                   ArticleKnowledgeIndexState current) {
        Decision decision = evaluate(incoming, current);
        if (decision != Decision.ACCEPT) {
            return current;
        }
        return new ArticleKnowledgeIndexState(
                incoming.getArticleId(),
                incoming.getArticleVersion(),
                incoming.getOperation(),
                incoming.getEventId(),
                incoming.getOccurredAt());
    }

    private static void validateCurrentState(ArticleKnowledgeEvent incoming,
                                             ArticleKnowledgeIndexState current) {
        if (current.articleId() == null || current.articleVersion() == null || current.operation() == null) {
            throw new IllegalArgumentException("current article knowledge index state is incomplete");
        }
        if (!incoming.getArticleId().equals(current.articleId())) {
            throw new IllegalArgumentException("cannot compare article knowledge events from different articles");
        }
    }

    public enum Decision {
        ACCEPT,
        DUPLICATE,
        STALE
    }
}
