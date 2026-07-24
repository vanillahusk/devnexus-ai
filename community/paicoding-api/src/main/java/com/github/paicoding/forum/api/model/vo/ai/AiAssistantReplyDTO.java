package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 助手应答
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
public class AiAssistantReplyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long articleId;

    private String sessionId;

    private String answer;

    /**
     * 当前实际走的路由，如 ragent / local
     */
    private String route;

    /**
     * true 表示没有走主路由，进入降级
     */
    private Boolean degraded;

    /**
     * 降级原因
     */
    private String degradeReason;

    private List<AiAssistantReferenceDTO> references;

    private List<AiAssistantHistoryItemDTO> history;
}
