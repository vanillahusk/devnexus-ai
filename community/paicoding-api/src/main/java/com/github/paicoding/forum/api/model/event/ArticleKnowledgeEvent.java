package com.github.paicoding.forum.api.model.event;

import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * 文章知识索引事件 V1。
 *
 * <p>事件只描述“哪篇文章的哪个版本发生了什么变化”。正文仍以 MySQL 为事实源，
 * 消费者收到事件后按 articleId 读取在线且未删除的文章快照。</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ArticleKnowledgeEvent {
    public static final int CURRENT_EVENT_VERSION = 1;

    private Integer eventVersion = CURRENT_EVENT_VERSION;
    private String eventId;
    private String originalEventId;
    /** HTTP/日志关联 ID；可选字段，不能参与事件幂等或业务版本判断。 */
    private String traceId;
    private Long articleId;
    private Long articleVersion;
    private ArticleKnowledgeOperationEnum operation;
    private Long occurredAt;

    public static ArticleKnowledgeEvent create(Long articleId,
                                               Long articleVersion,
                                               ArticleKnowledgeOperationEnum operation) {
        ArticleKnowledgeEvent event = new ArticleKnowledgeEvent();
        event.eventId = UUID.randomUUID().toString();
        event.articleId = articleId;
        event.articleVersion = articleVersion;
        event.operation = operation;
        event.occurredAt = System.currentTimeMillis();
        event.validate();
        return event;
    }

    /** 历史消息缺少版本字段时按 V1 兼容。 */
    public int effectiveEventVersion() {
        return eventVersion == null ? CURRENT_EVENT_VERSION : eventVersion;
    }

    /**
     * 业务幂等键。eventId 用于识别同一物理消息，业务键用于识别重复生成的等价事件。
     */
    public String idempotencyKey() {
        validate();
        return articleId + ":" + articleVersion + ":" + operation.name();
    }

    public void validate() {
        if (effectiveEventVersion() != CURRENT_EVENT_VERSION) {
            throw new IllegalArgumentException("unsupported article knowledge event version: " + eventVersion);
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("article knowledge eventId must not be blank");
        }
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("article knowledge articleId must be positive");
        }
        if (articleVersion == null || articleVersion <= 0) {
            throw new IllegalArgumentException("article knowledge articleVersion must be positive");
        }
        if (operation == null) {
            throw new IllegalArgumentException("article knowledge operation must not be null");
        }
        if (occurredAt == null || occurredAt <= 0) {
            throw new IllegalArgumentException("article knowledge occurredAt must be positive");
        }
    }
}
