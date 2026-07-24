package com.github.paicoding.forum.api.model.vo.ai.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 知识库文档展示对象
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
public class AiKnowledgeDocDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String type;

    private String code;

    private String key;

    private String title;

    private String content;

    /**
     * 导出到 ragent 时建议使用的 markdown 文本
     */
    private String exportMarkdown;
}
