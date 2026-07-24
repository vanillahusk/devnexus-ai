package com.github.paicoding.forum.service.ai.service;

import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeArticleSnapshotDTO;

/** 受控 Agent 使用的只读文章事实源。 */
public interface AiKnowledgeArticleReadService {
    AiKnowledgeArticleSnapshotDTO queryOnlineSnapshot(Long articleId);
}
