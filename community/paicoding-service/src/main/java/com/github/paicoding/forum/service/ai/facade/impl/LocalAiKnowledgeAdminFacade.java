package com.github.paicoding.forum.service.ai.facade.impl;

import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.service.ai.facade.AiKnowledgeAdminFacade;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.knowledge.service", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalAiKnowledgeAdminFacade implements AiKnowledgeAdminFacade {

    private final AiKnowledgeAdminService aiKnowledgeAdminService;

    @Override
    public void save(AiKnowledgeDocReq req) {
        aiKnowledgeAdminService.save(req);
    }

    @Override
    public PageVo<AiKnowledgeDocDTO> list(SearchAiKnowledgeDocReq req) {
        return aiKnowledgeAdminService.list(req);
    }

    @Override
    public void delete(Long id) {
        aiKnowledgeAdminService.delete(id);
    }

    @Override
    public List<AiKnowledgeDocDTO> exportDocs() {
        return aiKnowledgeAdminService.exportDocs();
    }

    @Override
    public void sync(Long id) {
        aiKnowledgeAdminService.sync(id);
    }

    @Override
    public void syncAll() {
        aiKnowledgeAdminService.syncAll();
    }
}
