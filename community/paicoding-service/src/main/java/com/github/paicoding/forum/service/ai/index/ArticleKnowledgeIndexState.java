package com.github.paicoding.forum.service.ai.index;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;

/** 消费者已经应用的文章索引状态快照。 */
public record ArticleKnowledgeIndexState(
        Long articleId,
        Long articleVersion,
        ArticleKnowledgeOperationEnum operation,
        String eventId,
        Long occurredAt) {
}
