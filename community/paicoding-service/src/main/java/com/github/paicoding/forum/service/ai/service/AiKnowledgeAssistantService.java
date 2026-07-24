package com.github.paicoding.forum.service.ai.service;

import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;

import java.util.List;

/**
 * AI 知识助手
 *
 * @author Codex
 * @date 2026-04-01
 */
public interface AiKnowledgeAssistantService {

    AiAssistantReplyDTO ask(AiAssistantAskReq req, Long userId);

    List<AiAssistantHistoryItemDTO> history(String sessionId, Long userId);
}
