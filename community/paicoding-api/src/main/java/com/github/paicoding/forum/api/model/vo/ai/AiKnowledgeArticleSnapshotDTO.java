package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

/**
 * 提供给受控 AI 工具的文章事实快照。
 *
 * <p>该对象只表示 MySQL 中当前可公开检索的 ONLINE 版本，不包含作者隐私、统计信息或用户足迹。</p>
 */
@Value
@Builder
public class AiKnowledgeArticleSnapshotDTO implements Serializable {
    Long articleId;
    Long articleVersion;
    String title;
    String summary;
    String content;
    Long categoryId;
    Long updatedAt;
}
