package com.github.paicoding.forum.service.ai.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.paicoding.forum.api.model.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("article_knowledge_index_state")
public class ArticleKnowledgeIndexStateDO extends BaseDO {
    private Long articleId;
    private Long articleVersion;
    private String operation;
    private String eventId;
    private String idempotencyKey;
    private Date syncedAt;
}
