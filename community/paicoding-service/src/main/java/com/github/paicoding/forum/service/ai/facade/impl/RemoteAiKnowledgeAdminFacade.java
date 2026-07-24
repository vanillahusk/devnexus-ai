package com.github.paicoding.forum.service.ai.facade.impl;

import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.service.ai.facade.AiKnowledgeAdminFacade;
import com.github.paicoding.forum.service.feign.FeignResultHelper;
import com.github.paicoding.forum.service.feign.aigc.AigcKnowledgeAdminFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.knowledge.service", name = "mode", havingValue = "remote")
public class RemoteAiKnowledgeAdminFacade implements AiKnowledgeAdminFacade {

    private final AigcKnowledgeAdminFeignClient aigcKnowledgeAdminFeignClient;

    @Override
    public void save(AiKnowledgeDocReq req) {
        FeignResultHelper.unwrap(aigcKnowledgeAdminFeignClient.save(req), "远端 AIGC 知识文档保存失败");
    }

    @Override
    public PageVo<AiKnowledgeDocDTO> list(SearchAiKnowledgeDocReq req) {
        return FeignResultHelper.unwrap(aigcKnowledgeAdminFeignClient.list(req), "远端 AIGC 知识文档列表查询失败");
    }

    @Override
    public void delete(Long id) {
        FeignResultHelper.unwrap(aigcKnowledgeAdminFeignClient.delete(id), "远端 AIGC 知识文档删除失败");
    }

    @Override
    public List<AiKnowledgeDocDTO> exportDocs() {
        return FeignResultHelper.unwrap(aigcKnowledgeAdminFeignClient.exportDocs(), "远端 AIGC 知识文档导出失败");
    }

    @Override
    public void sync(Long id) {
        FeignResultHelper.unwrap(aigcKnowledgeAdminFeignClient.sync(id), "远端 AIGC 知识文档同步失败");
    }

    @Override
    public void syncAll() {
        FeignResultHelper.unwrap(aigcKnowledgeAdminFeignClient.syncAll(), "远端 AIGC 全量知识文档同步失败");
    }
}
