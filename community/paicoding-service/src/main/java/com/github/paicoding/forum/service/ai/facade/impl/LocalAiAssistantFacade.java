package com.github.paicoding.forum.service.ai.facade.impl;

import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;
import com.github.paicoding.forum.service.ai.facade.AiAssistantFacade;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.knowledge.service", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalAiAssistantFacade implements AiAssistantFacade {

    private final AiKnowledgeAssistantService aiKnowledgeAssistantService;

    @Override
    public AiAssistantReplyDTO ask(AiAssistantAskReq req, Long userId) {
        return aiKnowledgeAssistantService.ask(req, userId);
    }

    @Override
    public List<AiAssistantHistoryItemDTO> history(String sessionId, Long userId) {
        return aiKnowledgeAssistantService.history(sessionId, userId);
    }
}
