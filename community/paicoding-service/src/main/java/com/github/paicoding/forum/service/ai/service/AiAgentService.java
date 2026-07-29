package com.github.paicoding.forum.service.ai.service;

import com.github.paicoding.forum.api.model.vo.ai.AiAgentAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAgentReplyDTO;

/**
 * 社区到 Ragent 受控 Agent 的安全门面。
 */
public interface AiAgentService {

    AiAgentReplyDTO query(AiAgentAskReq request, Long userId);
}
