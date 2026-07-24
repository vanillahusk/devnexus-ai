package com.github.paicoding.forum.service.notify.facade.impl;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.facade.NotifyCommandFacade;
import com.github.paicoding.forum.service.notify.service.NotifyCommandService;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "message.service", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalNotifyCommandFacade implements NotifyCommandFacade {

    private final NotifyCommandService notifyCommandService;

    @Override
    public void saveCommentNotify(MessageQueueEvent<CommentDO> event) {
        notifyCommandService.saveCommentNotify(event);
    }

    @Override
    public void saveReplyNotify(MessageQueueEvent<CommentDO> event) {
        notifyCommandService.saveReplyNotify(event);
    }

    @Override
    public void saveArticleNotify(MessageQueueEvent<UserFootDO> event) {
        notifyCommandService.saveArticleNotify(event);
    }

    @Override
    public void saveArticleNotify(UserFootDO foot, NotifyTypeEnum notifyTypeEnum) {
        notifyCommandService.saveArticleNotify(foot, notifyTypeEnum);
    }

    @Override
    public void removeArticleNotify(MessageQueueEvent<UserFootDO> event) {
        notifyCommandService.removeArticleNotify(event);
    }

    @Override
    public void saveFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        notifyCommandService.saveFollowNotify(event);
    }

    @Override
    public void removeFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        notifyCommandService.removeFollowNotify(event);
    }

    @Override
    public void saveRegisterSystemNotify(Long userId) {
        notifyCommandService.saveRegisterSystemNotify(userId);
    }
}
