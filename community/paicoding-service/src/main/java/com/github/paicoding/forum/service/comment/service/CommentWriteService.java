package com.github.paicoding.forum.service.comment.service;

import com.github.paicoding.forum.api.model.enums.CommentAuditStatusEnum;
import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;

/**
 * 评论Service接口
 *
 * @author XuYifei
 * @date 2024-07-12
 */
public interface CommentWriteService {

    /**
     * 更新/保存评论
     *
     * @param commentSaveReq
     * @return
     */
    Long saveComment(CommentSaveReq commentSaveReq);
    Long saveCommentSync(CommentSaveReq commentSaveReq);

    /** RocketMQ 评论事件直接事务落库；eventId 由数据库唯一约束保证端到端幂等。 */
    Long saveCommentFromEvent(String eventId, CommentSaveReq commentSaveReq);

    /**
     * 删除评论
     *
     * @param commentId
     * @throws Exception
     */
    void deleteComment(Long commentId, Long userId);

    /**
     * 审核状态只能从待审核迁移到通过或拒绝。
     */
    void reviewComment(Long commentId, CommentAuditStatusEnum targetStatus);

}
