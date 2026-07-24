package com.github.paicoding.forum.service.comment.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.paicoding.forum.api.model.entity.BaseDO;
import com.github.paicoding.forum.core.senstive.ano.SensitiveField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论表
 *
 * @author XuYifei
 * @date 2024-07-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comment")
public class CommentDO extends BaseDO {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long articleId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 评论内容
     */
    @SensitiveField(bind = "content")
    private String content;

    /**
     * 父评论ID
     */
    private Long parentCommentId;

    /**
     * 顶级评论ID
     */
    private Long topCommentId;

    /**
     * 审核状态：0待审核，1已通过，2已拒绝
     */
    private Integer auditStatus;

    /**
     * 创建评论的消息事件 ID。同步创建时为空；异步创建时用于数据库级幂等。
     */
    private String sourceEventId;

    /**
     * 0未删除 1 已删除
     */
    private Integer deleted;
}
