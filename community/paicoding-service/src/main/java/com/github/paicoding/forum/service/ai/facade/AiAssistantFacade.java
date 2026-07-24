package com.github.paicoding.forum.service.ai.facade;

import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;

import java.util.List;

/**
 * AI 助手调用门面，便于后续本地实现与远端 AIGC 服务切换
 */
public interface AiAssistantFacade {

    AiAssistantReplyDTO ask(AiAssistantAskReq req, Long userId);

    List<AiAssistantHistoryItemDTO> history(String sessionId, Long userId);
}
