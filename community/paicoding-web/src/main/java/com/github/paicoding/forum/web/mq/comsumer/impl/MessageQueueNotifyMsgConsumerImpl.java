package com.github.paicoding.forum.web.mq.comsumer.impl;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.facade.NotifyCommandFacade;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import com.github.paicoding.forum.web.mq.comsumer.MessageQueueNotifyMsgConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @program: pai_coding
 * @description:
 * @author: XuYifei
 * @create: 2024-10-30
 */

@Service
@Slf4j
public class MessageQueueNotifyMsgConsumerImpl implements MessageQueueNotifyMsgConsumer {
    private final NotifyCommandFacade notifyCommandFacade;

    public MessageQueueNotifyMsgConsumerImpl(NotifyCommandFacade notifyCommandFacade) {
        this.notifyCommandFacade = notifyCommandFacade;
    }

    /**
     * 评论 + 回复
     *
     * @param event
     */
    @Override
    public void saveCommentNotify(MessageQueueEvent<CommentDO> event) {
        notifyCommandFacade.saveCommentNotify(event);
    }

    /**
     * 评论回复消息
     *
     * @param event
     */
    @Override
    public void saveReplyNotify(MessageQueueEvent<CommentDO> event) {
        notifyCommandFacade.saveReplyNotify(event);
    }

    /**
     * 点赞 + 收藏
     *
     * @param event
     */
    @Override
    public void saveArticleNotify(MessageQueueEvent<UserFootDO> event) {
        notifyCommandFacade.saveArticleNotify(event);
    }

    @Override
    public void saveArticleNotify(UserFootDO foot, NotifyTypeEnum notifyTypeEnum) {
        notifyCommandFacade.saveArticleNotify(foot, notifyTypeEnum);
    }

    /**
     * 取消点赞，取消收藏
     * @param event
     */
    @Override
    public void removeArticleNotify(MessageQueueEvent<UserFootDO> event) {
        notifyCommandFacade.removeArticleNotify(event);
    }

    /**
     * 关注
     *
     * @param event
     */
    @Override
    public void saveFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        notifyCommandFacade.saveFollowNotify(event);
    }

    /**
     * 取消关注
     *
     * @param event
     */
    @Override
    public void removeFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        notifyCommandFacade.removeFollowNotify(event);
    }

    @Override
    public void saveRegisterSystemNotify(Long userId) {
        notifyCommandFacade.saveRegisterSystemNotify(userId);
    }
}
