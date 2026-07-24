package com.github.paicoding.forum.service.feign.message;

import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class MessageNotifyCommandFallbackFactory implements FallbackFactory<MessageNotifyCommandFeignClient> {

    @Override
    public MessageNotifyCommandFeignClient create(Throwable cause) {
        return new MessageNotifyCommandFeignClient() {
            private ResVo<Boolean> unavailable() {
                return ResVo.fail(StatusEnum.UNEXPECT_ERROR, "消息服务暂时不可用，写操作未确认");
            }

            @Override public ResVo<Boolean> saveComment(MessageQueueEvent<CommentDO> event) { return unavailable(); }
            @Override public ResVo<Boolean> saveReply(MessageQueueEvent<CommentDO> event) { return unavailable(); }
            @Override public ResVo<Boolean> saveArticle(MessageQueueEvent<UserFootDO> event) { return unavailable(); }
            @Override public ResVo<Boolean> removeArticle(MessageQueueEvent<UserFootDO> event) { return unavailable(); }
            @Override public ResVo<Boolean> saveFollow(MessageQueueEvent<UserRelationDO> event) { return unavailable(); }
            @Override public ResVo<Boolean> removeFollow(MessageQueueEvent<UserRelationDO> event) { return unavailable(); }
            @Override public ResVo<Boolean> saveRegister(Long userId) { return unavailable(); }
        };
    }
}
