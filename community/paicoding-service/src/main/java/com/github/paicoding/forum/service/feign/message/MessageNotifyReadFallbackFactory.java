package com.github.paicoding.forum.service.feign.message;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MessageNotifyReadFallbackFactory implements FallbackFactory<MessageNotifyReadFeignClient> {

    @Override
    public MessageNotifyReadFeignClient create(Throwable cause) {
        return new MessageNotifyReadFeignClient() {
            @Override public ResVo<Integer> count(Long userId) { return ResVo.ok(0); }
            @Override public ResVo<Map<String, Integer>> unreadCounts(Long userId) { return ResVo.ok(Map.of()); }
            @Override public ResVo<PageListVo<NotifyMsgDTO>> list(String type, Long page, Long pageSize, Long userId) {
                return ResVo.ok(PageListVo.emptyVo());
            }
            @Override public ResVo<Page<NotifyMsgDTO>> page(String type, Integer currentPage, Integer pageSize, Long userId) {
                return ResVo.ok(new Page<>(currentPage, pageSize));
            }
        };
    }
}
