package com.github.paicoding.forum.message.controller.internal;

import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.config.MessageServiceProperties;
import com.github.paicoding.forum.service.notify.service.NotifyCommandService;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.repository.entity.UserRelationDO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "internal/message/notify/command")
public class NotifyCommandInternalRestController {

    private final NotifyCommandService notifyCommandService;
    private final MessageServiceProperties properties;
    private final MessageInternalAccessValidator validator;

    @PostMapping(path = "comment/save")
    public ResVo<Boolean> saveComment(@RequestBody MessageQueueEvent<CommentDO> event, HttpServletRequest request) {
        validate(request);
        notifyCommandService.saveCommentNotify(event);
        return ResVo.ok(Boolean.TRUE);
    }

    @PostMapping(path = "reply/save")
    public ResVo<Boolean> saveReply(@RequestBody MessageQueueEvent<CommentDO> event, HttpServletRequest request) {
        validate(request);
        notifyCommandService.saveReplyNotify(event);
        return ResVo.ok(Boolean.TRUE);
    }

    @PostMapping(path = "article/save")
    public ResVo<Boolean> saveArticle(@RequestBody MessageQueueEvent<UserFootDO> event, HttpServletRequest request) {
        validate(request);
        notifyCommandService.saveArticleNotify(event);
        return ResVo.ok(Boolean.TRUE);
    }

    @PostMapping(path = "article/remove")
    public ResVo<Boolean> removeArticle(@RequestBody MessageQueueEvent<UserFootDO> event, HttpServletRequest request) {
        validate(request);
        notifyCommandService.removeArticleNotify(event);
        return ResVo.ok(Boolean.TRUE);
    }

    @PostMapping(path = "follow/save")
    public ResVo<Boolean> saveFollow(@RequestBody MessageQueueEvent<UserRelationDO> event, HttpServletRequest request) {
        validate(request);
        notifyCommandService.saveFollowNotify(event);
        return ResVo.ok(Boolean.TRUE);
    }

    @PostMapping(path = "follow/remove")
    public ResVo<Boolean> removeFollow(@RequestBody MessageQueueEvent<UserRelationDO> event, HttpServletRequest request) {
        validate(request);
        notifyCommandService.removeFollowNotify(event);
        return ResVo.ok(Boolean.TRUE);
    }

    @PostMapping(path = "register/save")
    public ResVo<Boolean> saveRegister(@RequestParam("userId") Long userId, HttpServletRequest request) {
        validate(request);
        notifyCommandService.saveRegisterSystemNotify(userId);
        return ResVo.ok(Boolean.TRUE);
    }

    private void validate(HttpServletRequest request) {
        validator.validate(request.getHeader(properties.getTokenHeader()));
    }
}
