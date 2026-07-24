package com.github.paicoding.forum.service.ai.facade.impl;

import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;
import com.github.paicoding.forum.service.ai.facade.AiAssistantFacade;
import com.github.paicoding.forum.service.feign.FeignResultHelper;
import com.github.paicoding.forum.service.feign.aigc.AigcAssistantFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.knowledge.service", name = "mode", havingValue = "remote")
public class RemoteAiAssistantFacade implements AiAssistantFacade {

    private final AigcAssistantFeignClient aigcAssistantFeignClient;

    @Override
    public AiAssistantReplyDTO ask(AiAssistantAskReq req, Long userId) {
        return FeignResultHelper.unwrap(aigcAssistantFeignClient.ask(req, userId), "远端 AIGC 助手问答失败");
    }

    @Override
    public List<AiAssistantHistoryItemDTO> history(String sessionId, Long userId) {
        return FeignResultHelper.unwrap(aigcAssistantFeignClient.history(sessionId, userId), "远端 AIGC 助手历史记录查询失败");
    }
}
