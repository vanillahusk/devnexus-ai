package com.github.paicoding.forum.service.notify.facade.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.PageParam;
import com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO;
import com.github.paicoding.forum.service.feign.FeignResultHelper;
import com.github.paicoding.forum.service.feign.message.MessageNotifyReadFeignClient;
import com.github.paicoding.forum.service.notify.facade.NotifyReadFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "message.service", name = "mode", havingValue = "remote")
public class RemoteNotifyReadFacade implements NotifyReadFacade {

    private final MessageNotifyReadFeignClient messageNotifyReadFeignClient;

    @Override
    public int queryUserNotifyMsgCount(Long userId) {
        Integer result = FeignResultHelper.unwrap(messageNotifyReadFeignClient.count(userId), "远端消息服务未读总数查询失败");
        return result == null ? 0 : result;
    }

    @Override
    public PageListVo<NotifyMsgDTO> queryUserNotices(Long userId, NotifyTypeEnum type, PageParam page) {
        return FeignResultHelper.unwrap(
                messageNotifyReadFeignClient.list(type.name().toLowerCase(), page.getPageNum(), page.getPageSize(), userId),
                "远端消息服务通知列表查询失败");
    }

    @Override
    public Page<NotifyMsgDTO> queryUserNotices(Long userId, NotifyTypeEnum type, int currentPage, int pageSize) {
        return FeignResultHelper.unwrap(
                messageNotifyReadFeignClient.page(type.name().toLowerCase(), currentPage, pageSize, userId),
                "远端消息服务通知分页查询失败");
    }

    @Override
    public Map<String, Integer> queryUnreadCounts(Long userId) {
        return FeignResultHelper.unwrap(messageNotifyReadFeignClient.unreadCounts(userId), "远端消息服务未读分类查询失败");
    }
}
