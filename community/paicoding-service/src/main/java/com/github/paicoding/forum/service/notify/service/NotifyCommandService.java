package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;

public interface NotifyCommandService {

    void saveCommentNotify(MessageQueueEvent<CommentDO> event);

    void saveReplyNotify(MessageQueueEvent<CommentDO> event);

    void saveArticleNotify(MessageQueueEvent<UserFootDO> event);

    void saveArticleNotify(UserFootDO foot, NotifyTypeEnum notifyTypeEnum);

    void removeArticleNotify(MessageQueueEvent<UserFootDO> event);

    void saveFollowNotify(MessageQueueEvent<UserRelationDO> event);

    void removeFollowNotify(MessageQueueEvent<UserRelationDO> event);

    void saveRegisterSystemNotify(Long userId);
}
