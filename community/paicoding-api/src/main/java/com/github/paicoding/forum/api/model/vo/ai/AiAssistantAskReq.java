package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 知识助手提问请求
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
public class AiAssistantAskReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 当前文章上下文
     */
    private Long articleId;

    /**
     * 当前会话id，前端可复用
     */
    private String sessionId;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 是否把评论也作为召回上下文
     */
    private Boolean includeComments;

    /**
     * 是否透传给 ragent 的 deepThinking 开关
     */
    private Boolean deepThinking;
}
