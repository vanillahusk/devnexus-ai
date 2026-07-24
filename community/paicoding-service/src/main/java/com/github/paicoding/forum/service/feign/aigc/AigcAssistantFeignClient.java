package com.github.paicoding.forum.service.feign.aigc;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "${ai.knowledge.service.service-id:${ai.knowledge.service.serviceId:aigc-service}}",
        contextId = "aigcAssistantFeignClient",
        path = "${ai.knowledge.service.assistant-internal-path:${ai.knowledge.service.assistantInternalPath:/internal/aigc/assistant}}",
        configuration = AigcFeignConfiguration.class
)
public interface AigcAssistantFeignClient {

    @PostMapping("ask")
    ResVo<AiAssistantReplyDTO> ask(@RequestBody AiAssistantAskReq req,
                                   @RequestHeader(value = "X-AIGC-USER-ID", required = false) Long userId);

    @GetMapping("history")
    ResVo<List<AiAssistantHistoryItemDTO>> history(@RequestParam("sessionId") String sessionId,
                                                   @RequestHeader(value = "X-AIGC-USER-ID", required = false) Long userId);
}
