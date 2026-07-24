package com.github.paicoding.forum.service.notify.service.impl;

import com.github.paicoding.forum.api.model.enums.NotifyStatEnum;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.comment.repository.dao.CommentDao;
import com.github.paicoding.forum.service.notify.repository.dao.NotifyMsgDao;
import com.github.paicoding.forum.service.notify.repository.dao.NotifyProjectionStateDao;
import com.github.paicoding.forum.service.notify.repository.entity.NotifyMsgDO;
import com.github.paicoding.forum.service.notify.repository.entity.NotifyProjectionStateDO;
import com.github.paicoding.forum.service.notify.service.NotifyCommandService;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotifyCommandServiceImpl implements NotifyCommandService {

    private static final Long ADMIN_ID = 1L;

    private final ArticleDao articleDao;
    private final CommentDao commentDao;
    private final NotifyMsgDao notifyMsgDao;
    private final NotifyProjectionStateDao projectionStateDao;

    public NotifyCommandServiceImpl(ArticleDao articleDao,
                                    CommentDao commentDao,
                                    NotifyMsgDao notifyMsgDao,
                                    NotifyProjectionStateDao projectionStateDao) {
        this.articleDao = articleDao;
        this.commentDao = commentDao;
        this.notifyMsgDao = notifyMsgDao;
        this.projectionStateDao = projectionStateDao;
    }

    @Override
    public void saveCommentNotify(MessageQueueEvent<CommentDO> event) {
        NotifyMsgDO msg = new NotifyMsgDO();
        CommentDO comment = event.getContent();
        ArticleDO article = articleDao.getById(comment.getArticleId());
        msg.setNotifyUserId(article.getUserId())
                .setOperateUserId(comment.getUserId())
                .setRelatedId(article.getId())
                .setType(event.getNotifyType().getType())
                .setState(NotifyStatEnum.UNREAD.getStat())
                .setMsg(comment.getContent());
        notifyMsgDao.save(msg);
    }

    @Override
    public void saveReplyNotify(MessageQueueEvent<CommentDO> event) {
        NotifyMsgDO msg = new NotifyMsgDO();
        CommentDO comment = event.getContent();
        CommentDO parent = commentDao.getApprovedById(comment.getParentCommentId());
        msg.setNotifyUserId(parent.getUserId())
                .setOperateUserId(comment.getUserId())
                .setRelatedId(comment.getArticleId())
                .setType(event.getNotifyType().getType())
                .setState(NotifyStatEnum.UNREAD.getStat())
                .setMsg(comment.getContent());
        notifyMsgDao.save(msg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveArticleNotify(MessageQueueEvent<UserFootDO> event) {
        if (event.getNotifyType() == NotifyTypeEnum.PRAISE
                && applyFavorProjection(event, true)) {
            return;
        }
        doSaveArticleNotify(event.getContent(), event.getNotifyType());
    }

    @Override
    public void saveArticleNotify(UserFootDO foot, NotifyTypeEnum notifyTypeEnum) {
        doSaveArticleNotify(foot, notifyTypeEnum);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeArticleNotify(MessageQueueEvent<UserFootDO> event) {
        if (event.getNotifyType() == NotifyTypeEnum.CANCEL_PRAISE
                && applyFavorProjection(event, false)) {
            return;
        }
        doRemoveArticleNotify(event);
    }

    private void doRemoveArticleNotify(MessageQueueEvent<UserFootDO> event) {
        UserFootDO foot = event.getContent();
        NotifyMsgDO msg = new NotifyMsgDO()
                .setRelatedId(foot.getDocumentId())
                .setNotifyUserId(foot.getDocumentUserId())
                .setOperateUserId(foot.getUserId())
                .setType(event.getNotifyType().getType())
                .setMsg("");
        NotifyMsgDO record = notifyMsgDao.getByUserIdRelatedIdAndType(msg);
        if (record != null) {
            notifyMsgDao.removeById(record.getId());
        }
    }

    /**
     * @return true 表示该点赞事件已由版本投影处理（包括旧版本跳过）。
     */
    private boolean applyFavorProjection(MessageQueueEvent<UserFootDO> event, boolean desiredState) {
        UserFootDO foot = event.getContent();
        if (foot == null || foot.getFavorVersion() == null || foot.getFavorVersion() <= 0) {
            return false;
        }
        String aggregateKey = "PRAISE:" + foot.getUserId() + ':' + foot.getDocumentId();
        NotifyProjectionStateDO state = projectionStateDao.lockOrCreate(aggregateKey);
        if (state == null) {
            throw new IllegalStateException("notify projection state not found after insert: " + aggregateKey);
        }
        if (foot.getFavorVersion() <= state.getBusinessVersion()) {
            return true;
        }
        if (desiredState) {
            doSaveArticleNotify(foot, NotifyTypeEnum.PRAISE);
        } else {
            doRemoveArticleNotify(event);
        }
        if (!projectionStateDao.advance(state.getId(), state.getBusinessVersion(),
                foot.getFavorVersion(), desiredState)) {
            throw new IllegalStateException("notify projection version advance failed: " + aggregateKey);
        }
        return true;
    }

    @Override
    public void saveFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        UserRelationDO relation = event.getContent();
        NotifyMsgDO msg = new NotifyMsgDO().setRelatedId(0L)
                .setNotifyUserId(relation.getUserId())
                .setOperateUserId(relation.getFollowUserId())
                .setType(event.getNotifyType().getType())
                .setState(NotifyStatEnum.UNREAD.getStat())
                .setMsg("");
        NotifyMsgDO record = notifyMsgDao.getByUserIdRelatedIdAndType(msg);
        if (record == null) {
            notifyMsgDao.save(msg);
        }
    }

    @Override
    public void removeFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        UserRelationDO relation = event.getContent();
        NotifyMsgDO msg = new NotifyMsgDO()
                .setRelatedId(0L)
                .setNotifyUserId(relation.getUserId())
                .setOperateUserId(relation.getFollowUserId())
                .setType(event.getNotifyType().getType())
                .setMsg("");
        NotifyMsgDO record = notifyMsgDao.getByUserIdRelatedIdAndType(msg);
        if (record != null) {
            notifyMsgDao.removeById(record.getId());
        }
    }

    @Override
    public void saveRegisterSystemNotify(Long userId) {
        NotifyMsgDO msg = new NotifyMsgDO().setRelatedId(0L)
                .setNotifyUserId(userId)
                .setOperateUserId(ADMIN_ID)
                .setType(NotifyTypeEnum.REGISTER.getType())
                .setState(NotifyStatEnum.UNREAD.getStat())
                .setMsg(SpringUtil.getConfig("view.site.welcomeInfo"));
        NotifyMsgDO record = notifyMsgDao.getByUserIdRelatedIdAndType(msg);
        if (record == null) {
            notifyMsgDao.save(msg);
        }
    }

    private void doSaveArticleNotify(UserFootDO foot, NotifyTypeEnum notifyTypeEnum) {
        NotifyMsgDO msg = new NotifyMsgDO().setRelatedId(foot.getDocumentId())
                .setNotifyUserId(foot.getDocumentUserId())
                .setOperateUserId(foot.getUserId())
                .setType(notifyTypeEnum.getType())
                .setState(NotifyStatEnum.UNREAD.getStat())
                .setMsg("");
        NotifyMsgDO record = notifyMsgDao.getByUserIdRelatedIdAndType(msg);
        if (record == null) {
            notifyMsgDao.save(msg);
        }
    }
}
