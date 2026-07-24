package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 助手会话历史
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
public class AiAssistantHistoryItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String question;

    private String answer;

    private String askTime;

    private String route;

    private Boolean degraded;
}
