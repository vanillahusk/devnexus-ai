package com.github.paicoding.forum.service.feign.message;

import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "${message.service.service-id:${message.service.serviceId:message-service}}",
        contextId = "messageNotifyCommandFeignClient",
        path = "${message.service.command-internal-path:${message.service.commandInternalPath:/internal/message/notify/command}}",
        configuration = MessageFeignConfiguration.class,
        fallbackFactory = MessageNotifyCommandFallbackFactory.class
)
public interface MessageNotifyCommandFeignClient {

    @PostMapping("comment/save")
    ResVo<Boolean> saveComment(@RequestBody MessageQueueEvent<CommentDO> event);

    @PostMapping("reply/save")
    ResVo<Boolean> saveReply(@RequestBody MessageQueueEvent<CommentDO> event);

    @PostMapping("article/save")
    ResVo<Boolean> saveArticle(@RequestBody MessageQueueEvent<UserFootDO> event);

    @PostMapping("article/remove")
    ResVo<Boolean> removeArticle(@RequestBody MessageQueueEvent<UserFootDO> event);

    @PostMapping("follow/save")
    ResVo<Boolean> saveFollow(@RequestBody MessageQueueEvent<UserRelationDO> event);

    @PostMapping("follow/remove")
    ResVo<Boolean> removeFollow(@RequestBody MessageQueueEvent<UserRelationDO> event);

    @PostMapping("register/save")
    ResVo<Boolean> saveRegister(@RequestParam("userId") Long userId);
}
