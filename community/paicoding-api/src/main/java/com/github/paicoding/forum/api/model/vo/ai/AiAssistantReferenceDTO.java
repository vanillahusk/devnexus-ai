package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 助手命中的知识来源
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
public class AiAssistantReferenceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sourceType;

    private String title;

    private String snippet;

    private Long articleId;

    private Long commentId;

    private String configKey;
}
