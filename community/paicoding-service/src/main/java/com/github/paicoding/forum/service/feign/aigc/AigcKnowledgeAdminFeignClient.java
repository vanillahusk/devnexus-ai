package com.github.paicoding.forum.service.feign.aigc;

import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "${ai.knowledge.service.service-id:${ai.knowledge.service.serviceId:aigc-service}}",
        contextId = "aigcKnowledgeAdminFeignClient",
        path = "${ai.knowledge.service.admin-internal-path:${ai.knowledge.service.adminInternalPath:/internal/aigc/admin/knowledge}}",
        configuration = AigcFeignConfiguration.class
)
public interface AigcKnowledgeAdminFeignClient {

    @PostMapping("save")
    ResVo<String> save(@RequestBody AiKnowledgeDocReq req);

    @PostMapping("list")
    ResVo<PageVo<AiKnowledgeDocDTO>> list(@RequestBody SearchAiKnowledgeDocReq req);

    @GetMapping("delete")
    ResVo<String> delete(@RequestParam("id") Long id);

    @GetMapping("export")
    ResVo<List<AiKnowledgeDocDTO>> exportDocs();

    @GetMapping("sync")
    ResVo<String> sync(@RequestParam("id") Long id);

    @GetMapping("syncAll")
    ResVo<String> syncAll();
}
