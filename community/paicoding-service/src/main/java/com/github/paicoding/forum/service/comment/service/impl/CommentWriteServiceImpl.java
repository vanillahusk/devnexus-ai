package com.github.paicoding.forum.service.comment.service.impl;

import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.enums.CommentAuditStatusEnum;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.core.util.NumUtil;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.service.comment.converter.CommentConverter;
import com.github.paicoding.forum.service.comment.repository.dao.CommentDao;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.comment.service.CommentHotspotGovernanceService;
import com.github.paicoding.forum.service.comment.service.CommentRateLimitService;
import com.github.paicoding.forum.service.comment.service.CommentWriteService;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import com.github.paicoding.forum.service.user.service.UserFootService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

/**
 * 评论Service
 *
 * @author XuYifei
 * @date 2024-07-12
 */
@Service
public class CommentWriteServiceImpl implements CommentWriteService {
    @Autowired
    private CommentDao commentDao;

    @Autowired
    private ArticleReadService articleReadService;

    @Autowired
    private UserFootService userFootWriteService;

    @Autowired
    private CommentHotspotGovernanceService commentHotspotGovernanceService;

    @Autowired
    private CommentRateLimitService commentRateLimitService;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    private MqOutboxService mqOutboxService;

    /**
     * 独立于 MQ 总开关，便于故障降级和用同一套基础设施做同步/异步基线对比。
     */
    @Value("${paicoding.comment.async-enabled:true}")
    private boolean commentAsyncEnabled;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveComment(CommentSaveReq commentSaveReq) {
        boolean creating = NumUtil.nullOrZero(commentSaveReq.getCommentId());
        if (creating) {
            commentRateLimitService.check(commentSaveReq.getArticleId(), commentSaveReq.getTopCommentId());
        }
        if (creating && commentAsyncEnabled && messageQueueService.enabled()) {
            messageQueueService.publish(new MessageQueueEvent<>(NotifyTypeEnum.COMMENT,
                    commentSaveReq,
                    commentSaveReq.getUserId()), CommonConstants.ROCKETMQ_TAG_COMMENT_WRITE);
            return -1L;
        }

        Long commentId = saveCommentSync(commentSaveReq);
        return creating && commentId != null ? -commentId : commentId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCommentSync(CommentSaveReq commentSaveReq) {
        // 保存评论
        CommentDO comment;
        if (NumUtil.nullOrZero(commentSaveReq.getCommentId())) {
            comment = addComment(commentSaveReq);
        } else {
            comment = updateComment(commentSaveReq);
        }
        if (Objects.equals(comment.getAuditStatus(), CommentAuditStatusEnum.APPROVED.getCode())) {
            commentHotspotGovernanceService.onCommentChanged(comment.getArticleId());
        }
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCommentFromEvent(String eventId, CommentSaveReq commentSaveReq) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("comment eventId must not be blank");
        }
        if (!NumUtil.nullOrZero(commentSaveReq.getCommentId())) {
            throw new IllegalArgumentException("comment write event only supports new comments");
        }
        CommentDO existing = commentDao.getBySourceEventId(eventId);
        if (existing != null) {
            return existing.getId();
        }
        CommentDO comment = addComment(commentSaveReq, eventId);
        return comment.getId();
    }

    private CommentDO addComment(CommentSaveReq commentSaveReq) {
        return addComment(commentSaveReq, null);
    }

    private CommentDO addComment(CommentSaveReq commentSaveReq, String sourceEventId) {
        // 回复只能挂到已通过且可见的父评论下。
        getApprovedParentCommentUser(commentSaveReq.getParentCommentId());
        if (articleReadService.queryBasicArticle(commentSaveReq.getArticleId()) == null) {
            throw ExceptionUtil.of(StatusEnum.ARTICLE_NOT_EXISTS, commentSaveReq.getArticleId());
        }

        // 1. 保存评论内容
        CommentDO commentDO = CommentConverter.toDo(commentSaveReq);
        commentDO.setSourceEventId(sourceEventId);
        Date now = new Date();
        commentDO.setCreateTime(now);
        commentDO.setUpdateTime(now);
        commentDao.save(commentDO);

        return commentDO;
    }

    private CommentDO updateComment(CommentSaveReq commentSaveReq) {
        // 更新评论
        CommentDO commentDO = commentDao.getById(commentSaveReq.getCommentId());
        if (commentDO == null) {
            throw ExceptionUtil.of(StatusEnum.COMMENT_NOT_EXISTS, commentSaveReq.getCommentId());
        }
        commentDO.setContent(commentSaveReq.getCommentContent());
        commentDao.updateById(commentDO);
        return commentDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long userId) {
        CommentDO commentDO = commentDao.getById(commentId);
        // 1.校验评论，是否越权，文章是否存在
        if (commentDO == null) {
            throw ExceptionUtil.of(StatusEnum.COMMENT_NOT_EXISTS, "评论ID=" + commentId);
        }
        if (!Objects.equals(commentDO.getUserId(), userId)) {
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "无权删除评论");
        }
        // 获取文章信息
        ArticleDO article = articleReadService.queryBasicArticle(commentDO.getArticleId());
        if (article == null) {
            throw ExceptionUtil.of(StatusEnum.ARTICLE_NOT_EXISTS, commentDO.getArticleId());
        }

        // 2.删除评论、足迹
        commentDO.setDeleted(YesOrNoEnum.YES.getCode());
        commentDao.updateById(commentDO);
        boolean approved = Objects.equals(commentDO.getAuditStatus(), CommentAuditStatusEnum.APPROVED.getCode());
        if (approved) {
            userFootWriteService.removeCommentFoot(commentDO, article.getUserId(), getApprovedParentCommentUser(commentDO.getParentCommentId()));
        }

        if (approved && NumUtil.upZero(commentDO.getTopCommentId())) {
            commentHotspotGovernanceService.onReplyDelta(commentDO.getArticleId(), commentDO.getTopCommentId(), -1);
        }
        if (approved) {
            commentHotspotGovernanceService.onCommentChanged(commentDO.getArticleId());
        }

        // 3. 发布删除评论事件
        if (approved) {
            SpringUtil.publishEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.DELETE_COMMENT, commentDO));
        }
        if (approved && NumUtil.upZero(commentDO.getParentCommentId())) {
            // 评论
            SpringUtil.publishEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.DELETE_REPLY, commentDO));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewComment(Long commentId, CommentAuditStatusEnum targetStatus) {
        if (targetStatus != CommentAuditStatusEnum.APPROVED && targetStatus != CommentAuditStatusEnum.REJECTED) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "审核目标状态必须是已通过或已拒绝");
        }
        CommentDO comment = commentDao.getById(commentId);
        if (comment == null || Objects.equals(comment.getDeleted(), YesOrNoEnum.YES.getCode())) {
            throw ExceptionUtil.of(StatusEnum.COMMENT_NOT_EXISTS, commentId);
        }
        if (!Objects.equals(comment.getAuditStatus(), CommentAuditStatusEnum.PENDING.getCode())) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "评论不处于待审核状态");
        }

        ArticleDO article = null;
        Long parentCommentUser = null;
        if (targetStatus == CommentAuditStatusEnum.APPROVED) {
            article = articleReadService.queryBasicArticle(comment.getArticleId());
            if (article == null) {
                throw ExceptionUtil.of(StatusEnum.ARTICLE_NOT_EXISTS, comment.getArticleId());
            }
            parentCommentUser = getApprovedParentCommentUser(comment.getParentCommentId());
        }
        if (!commentDao.transitionAuditStatus(commentId, targetStatus)) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "评论审核状态已被其他请求修改");
        }
        comment.setAuditStatus(targetStatus.getCode());
        if (targetStatus == CommentAuditStatusEnum.APPROVED) {
            activateApprovedComment(comment, article, parentCommentUser);
        }
    }

    private void activateApprovedComment(CommentDO comment, ArticleDO article, Long parentCommentUser) {
        userFootWriteService.saveCommentFoot(comment, article.getUserId(), parentCommentUser);
        NotifyTypeEnum notifyType = NumUtil.upZero(parentCommentUser) ? NotifyTypeEnum.REPLY : NotifyTypeEnum.COMMENT;
        mqOutboxService.saveCommentNotify(comment, notifyType);
        // 本地领域事件仍用于活跃度、统计等派生数据；站内通知由 Outbox + RocketMQ 独立投递。
        SpringUtil.publishEvent(new NotifyMsgEvent<>(this, notifyType, comment));
        if (NumUtil.upZero(comment.getTopCommentId())) {
            commentHotspotGovernanceService.onReplyDelta(comment.getArticleId(), comment.getTopCommentId(), 1);
        }
        commentHotspotGovernanceService.onCommentChanged(comment.getArticleId());
    }


    private Long getApprovedParentCommentUser(Long parentCommentId) {
        if (NumUtil.nullOrZero(parentCommentId)) {
            return null;

        }
        CommentDO parent = commentDao.getById(parentCommentId);
        if (parent == null || !Objects.equals(parent.getAuditStatus(), CommentAuditStatusEnum.APPROVED.getCode())
                || Objects.equals(parent.getDeleted(), YesOrNoEnum.YES.getCode())) {
            throw ExceptionUtil.of(StatusEnum.COMMENT_NOT_EXISTS, "可见父评论=" + parentCommentId);
        }
        return parent.getUserId();
    }

}
