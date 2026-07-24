package com.github.paicoding.forum.web.controller.notice.internal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.PageParam;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO;
import com.github.paicoding.forum.service.notify.config.MessageServiceProperties;
import com.github.paicoding.forum.service.notify.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "internal/message/notify")
public class NotifyReadInternalRestController {

    private final NotifyService notifyService;
    private final MessageServiceProperties properties;
    private final MessageInternalAccessValidator validator;

    @GetMapping(path = "count")
    public ResVo<Integer> count(HttpServletRequest request) {
        Long userId = parseUserId(request);
        validator.validate(request.getHeader(properties.getTokenHeader()));
        return ResVo.ok(notifyService.queryUserNotifyMsgCount(userId));
    }

    @GetMapping(path = "unreadCounts")
    public ResVo<Map<String, Integer>> unreadCounts(HttpServletRequest request) {
        Long userId = parseUserId(request);
        validator.validate(request.getHeader(properties.getTokenHeader()));
        return ResVo.ok(notifyService.queryUnreadCounts(userId));
    }

    @GetMapping(path = "list")
    public ResVo<PageListVo<NotifyMsgDTO>> list(@RequestParam("type") String type,
                                                @RequestParam("page") Long page,
                                                @RequestParam("pageSize") Long pageSize,
                                                HttpServletRequest request) {
        Long userId = parseUserId(request);
        validator.validate(request.getHeader(properties.getTokenHeader()));
        return ResVo.ok(notifyService.queryUserNotices(
                userId,
                NotifyTypeEnum.typeOf(type),
                PageParam.newPageInstance(page, pageSize)
        ));
    }

    @GetMapping(path = "page")
    public ResVo<Page<NotifyMsgDTO>> page(@RequestParam("type") String type,
                                          @RequestParam("currentPage") Integer currentPage,
                                          @RequestParam("pageSize") Integer pageSize,
                                          HttpServletRequest request) {
        Long userId = parseUserId(request);
        validator.validate(request.getHeader(properties.getTokenHeader()));
        return ResVo.ok((Page<NotifyMsgDTO>) notifyService.queryUserNotices(
                userId,
                NotifyTypeEnum.typeOf(type),
                currentPage,
                pageSize
        ));
    }

    private Long parseUserId(HttpServletRequest request) {
        String value = request.getHeader(properties.getUserIdHeader());
        return value == null ? null : Long.valueOf(value);
    }
}
