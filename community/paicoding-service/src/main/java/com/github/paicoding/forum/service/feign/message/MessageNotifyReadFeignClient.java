package com.github.paicoding.forum.service.feign.message;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "${message.service.service-id:${message.service.serviceId:message-service}}",
        contextId = "messageNotifyReadFeignClient",
        path = "${message.service.read-internal-path:${message.service.readInternalPath:/internal/message/notify}}",
        configuration = MessageFeignConfiguration.class,
        fallbackFactory = MessageNotifyReadFallbackFactory.class
)
public interface MessageNotifyReadFeignClient {

    @GetMapping("count")
    ResVo<Integer> count(@RequestHeader(value = "X-MESSAGE-USER-ID", required = false) Long userId);

    @GetMapping("unreadCounts")
    ResVo<Map<String, Integer>> unreadCounts(@RequestHeader(value = "X-MESSAGE-USER-ID", required = false) Long userId);

    @GetMapping("list")
    ResVo<PageListVo<NotifyMsgDTO>> list(@RequestParam("type") String type,
                                         @RequestParam("page") Long page,
                                         @RequestParam("pageSize") Long pageSize,
                                         @RequestHeader(value = "X-MESSAGE-USER-ID", required = false) Long userId);

    @GetMapping("page")
    ResVo<Page<NotifyMsgDTO>> page(@RequestParam("type") String type,
                                   @RequestParam("currentPage") Integer currentPage,
                                   @RequestParam("pageSize") Integer pageSize,
                                   @RequestHeader(value = "X-MESSAGE-USER-ID", required = false) Long userId);
}
