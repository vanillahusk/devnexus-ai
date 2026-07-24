package com.github.paicoding.forum.service.notify.facade.impl;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.service.feign.FeignResultHelper;
import com.github.paicoding.forum.service.feign.message.MessageNotifyCommandFeignClient;
import com.github.paicoding.forum.service.notify.facade.NotifyCommandFacade;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "message.service", name = "mode", havingValue = "remote")
public class RemoteNotifyCommandFacade implements NotifyCommandFacade {

    private final MessageNotifyCommandFeignClient messageNotifyCommandFeignClient;

    @Override
    public void saveCommentNotify(MessageQueueEvent<CommentDO> event) {
        FeignResultHelper.unwrap(messageNotifyCommandFeignClient.saveComment(event), "远端消息服务保存评论通知失败");
    }

    @Override
    public void saveReplyNotify(MessageQueueEvent<CommentDO> event) {
        FeignResultHelper.unwrap(messageNotifyCommandFeignClient.saveReply(event), "远端消息服务保存回复通知失败");
    }

    @Override
    public void saveArticleNotify(MessageQueueEvent<UserFootDO> event) {
        FeignResultHelper.unwrap(messageNotifyCommandFeignClient.saveArticle(event), "远端消息服务保存文章通知失败");
    }

    @Override
    public void saveArticleNotify(UserFootDO foot, NotifyTypeEnum notifyTypeEnum) {
        FeignResultHelper.unwrap(
                messageNotifyCommandFeignClient.saveArticle(new MessageQueueEvent<>(notifyTypeEnum, foot)),
                "远端消息服务保存文章通知失败");
    }

    @Override
    public void removeArticleNotify(MessageQueueEvent<UserFootDO> event) {
        FeignResultHelper.unwrap(messageNotifyCommandFeignClient.removeArticle(event), "远端消息服务删除文章通知失败");
    }

    @Override
    public void saveFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        FeignResultHelper.unwrap(messageNotifyCommandFeignClient.saveFollow(event), "远端消息服务保存关注通知失败");
    }

    @Override
    public void removeFollowNotify(MessageQueueEvent<UserRelationDO> event) {
        FeignResultHelper.unwrap(messageNotifyCommandFeignClient.removeFollow(event), "远端消息服务删除关注通知失败");
    }

    @Override
    public void saveRegisterSystemNotify(Long userId) {
        FeignResultHelper.unwrap(messageNotifyCommandFeignClient.saveRegister(userId), "远端消息服务保存注册通知失败");
    }
}
