package com.github.paicoding.forum.service.ai.index;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;

/** 可替换的远端文章索引边界。 */
public interface ArticleKnowledgeIndexer {
    /**
     * 以 MySQL 当前文章快照为事实源收敛远端索引。
     *
     * <p>消息中的版本只是最低水位：若消息到达时文章已经更新，索引器直接应用
     * 当前最新快照，避免为了过期正文逐版本重放。</p>
     */
    ApplyResult converge(Long articleId, Long eventVersion,
                         ArticleKnowledgeOperationEnum eventOperation);

    record ApplyResult(Long articleVersion, ArticleKnowledgeOperationEnum operation) {
        public ApplyResult {
            if (articleVersion == null || articleVersion <= 0 || operation == null) {
                throw new IllegalArgumentException("article knowledge apply result is incomplete");
            }
        }
    }
}
