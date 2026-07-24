package com.github.paicoding.forum.service.notify.facade.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.PageParam;
import com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO;
import com.github.paicoding.forum.service.notify.facade.NotifyReadFacade;
import com.github.paicoding.forum.service.notify.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "message.service", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalNotifyReadFacade implements NotifyReadFacade {

    private final NotifyService notifyService;

    @Override
    public int queryUserNotifyMsgCount(Long userId) {
        return notifyService.queryUserNotifyMsgCount(userId);
    }

    @Override
    public PageListVo<NotifyMsgDTO> queryUserNotices(Long userId, NotifyTypeEnum type, PageParam page) {
        return notifyService.queryUserNotices(userId, type, page);
    }

    @Override
    public Page<NotifyMsgDTO> queryUserNotices(Long userId, NotifyTypeEnum type, int currentPage, int pageSize) {
        return (Page<NotifyMsgDTO>) notifyService.queryUserNotices(userId, type, currentPage, pageSize);
    }

    @Override
    public Map<String, Integer> queryUnreadCounts(Long userId) {
        return notifyService.queryUnreadCounts(userId);
    }
}
