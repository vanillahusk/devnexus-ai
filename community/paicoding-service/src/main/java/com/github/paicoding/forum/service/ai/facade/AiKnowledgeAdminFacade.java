package com.github.paicoding.forum.service.ai.facade;

import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;

import java.util.List;

/**
 * AI 知识后台调用门面，便于后续拆分为独立 AIGC 服务
 */
public interface AiKnowledgeAdminFacade {

    void save(AiKnowledgeDocReq req);

    PageVo<AiKnowledgeDocDTO> list(SearchAiKnowledgeDocReq req);

    void delete(Long id);

    List<AiKnowledgeDocDTO> exportDocs();

    void sync(Long id);

    void syncAll();
}
