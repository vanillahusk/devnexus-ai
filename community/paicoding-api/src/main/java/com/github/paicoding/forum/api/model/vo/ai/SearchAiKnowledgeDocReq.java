package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 知识库文档查询请求
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
public class SearchAiKnowledgeDocReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;

    private String keyword;

    private Long pageNumber;

    private Long pageSize;
}
