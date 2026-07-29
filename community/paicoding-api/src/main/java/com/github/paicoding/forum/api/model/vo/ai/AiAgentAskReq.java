package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 社区受控 Agent 请求。
 */
@Data
public class AiAgentAskReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;

    private String question;
}
