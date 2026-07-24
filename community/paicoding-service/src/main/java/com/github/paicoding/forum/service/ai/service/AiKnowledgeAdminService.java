package com.github.paicoding.forum.service.ai.service;

import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;

import java.util.List;

/**
 * AI 知识库管理
 *
 * @author Codex
 * @date 2026-04-01
 */
public interface AiKnowledgeAdminService {

    PageVo<AiKnowledgeDocDTO> list(SearchAiKnowledgeDocReq req);

    void save(AiKnowledgeDocReq req);

    void delete(Long id);

    List<AiKnowledgeDocDTO> exportDocs();

    void sync(Long id);

    void syncAll();
}
