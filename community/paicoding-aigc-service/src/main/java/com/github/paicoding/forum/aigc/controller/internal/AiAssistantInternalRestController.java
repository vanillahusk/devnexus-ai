package com.github.paicoding.forum.aigc.controller.internal;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeAssistantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "internal/aigc/assistant")
public class AiAssistantInternalRestController {

    private final AiKnowledgeProperties properties;
    private final AiInternalAccessValidator aiInternalAccessValidator;
    private final AiKnowledgeAssistantService aiKnowledgeAssistantService;

    @PostMapping(path = "ask")
    public ResVo<AiAssistantReplyDTO> ask(@RequestBody AiAssistantAskReq req, HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        Long userId = parseUserId(request);
        return ResVo.ok(aiKnowledgeAssistantService.ask(req, userId));
    }

    @GetMapping(path = "history")
    public ResVo<List<AiAssistantHistoryItemDTO>> history(@RequestParam("sessionId") String sessionId,
                                                          HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        Long userId = parseUserId(request);
        return ResVo.ok(aiKnowledgeAssistantService.history(sessionId, userId));
    }

    private Long parseUserId(HttpServletRequest request) {
        String value = request.getHeader(properties.getService().getUserIdHeader());
        return value == null ? null : Long.valueOf(value);
    }
}
