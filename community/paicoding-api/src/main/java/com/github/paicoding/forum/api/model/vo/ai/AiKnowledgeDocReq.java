package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 知识库文档写入请求
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
public class AiKnowledgeDocReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 文档类型：rule / faq / qa
     */
    private String type;

    /**
     * 文档编码
     */
    private String code;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档正文
     */
    private String content;
}
