package com.github.paicoding.forum.service.notify.facade;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.PageParam;
import com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO;

import java.util.Map;

public interface NotifyReadFacade {

    int queryUserNotifyMsgCount(Long userId);

    PageListVo<NotifyMsgDTO> queryUserNotices(Long userId, NotifyTypeEnum type, PageParam page);

    Page<NotifyMsgDTO> queryUserNotices(Long userId, NotifyTypeEnum type, int currentPage, int pageSize);

    Map<String, Integer> queryUnreadCounts(Long userId);
}
