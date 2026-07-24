package com.github.paicoding.forum.service.notify.service.impl;

import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.facade.NotifyCommandFacade;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author XuYifei
 * @date 2024-07-12
 */
@Slf4j
@Async
@Service
public class NotifyMsgListener implements ApplicationListener<NotifyMsgEvent<?>> {
    private final NotifyCommandFacade notifyCommandFacade;

    public NotifyMsgListener(NotifyCommandFacade notifyCommandFacade) {
        this.notifyCommandFacade = notifyCommandFacade;
    }

    @Override
    public void onApplicationEvent(NotifyMsgEvent<?> msgEvent) {
        switch (msgEvent.getNotifyType()) {
            case COMMENT:
            case REPLY:
                // 评论通知由审核事务内的 Outbox 可靠投递，避免本地异步事件丢失或重复写通知。
                break;
            case PRAISE:
            case COLLECT:
                notifyCommandFacade.saveArticleNotify(new MessageQueueEvent<>(msgEvent.getNotifyType(), (UserFootDO) msgEvent.getContent()));
                break;
            case CANCEL_PRAISE:
            case CANCEL_COLLECT:
                notifyCommandFacade.removeArticleNotify(new MessageQueueEvent<>(msgEvent.getNotifyType(), (UserFootDO) msgEvent.getContent()));
                break;
            case FOLLOW:
                notifyCommandFacade.saveFollowNotify(new MessageQueueEvent<>(msgEvent.getNotifyType(), (UserRelationDO) msgEvent.getContent()));
                break;
            case CANCEL_FOLLOW:
                notifyCommandFacade.removeFollowNotify(new MessageQueueEvent<>(msgEvent.getNotifyType(), (UserRelationDO) msgEvent.getContent()));
                break;
            case LOGIN:
                // todo 用户登录，判断是否需要插入新的通知消息，暂时先不做
                break;
            case REGISTER:
                // 首次注册，插入一个欢迎的消息
                notifyCommandFacade.saveRegisterSystemNotify((Long) msgEvent.getContent());
                break;
            default:
                // todo 系统消息
        }
    }
}
