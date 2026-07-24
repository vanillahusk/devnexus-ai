package com.github.paicoding.forum.api.model.enums.ai;

/**
 * 文章知识索引操作。
 *
 * <p>同一文章版本发生乱序时，优先级越高的状态越不能被低优先级事件覆盖：
 * OFFLINE &gt; UPDATE &gt; ONLINE。</p>
 */
public enum ArticleKnowledgeOperationEnum {
    ONLINE(1),
    UPDATE(2),
    OFFLINE(3);

    private final int precedence;

    ArticleKnowledgeOperationEnum(int precedence) {
        this.precedence = precedence;
    }

    public int precedence() {
        return precedence;
    }
}
