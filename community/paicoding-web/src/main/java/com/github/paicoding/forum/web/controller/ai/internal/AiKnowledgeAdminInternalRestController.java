package com.github.paicoding.forum.web.controller.ai.internal;

import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "internal/aigc/admin/knowledge")
public class AiKnowledgeAdminInternalRestController {

    private final AiKnowledgeProperties properties;
    private final AiInternalAccessValidator aiInternalAccessValidator;
    private final AiKnowledgeAdminService aiKnowledgeAdminService;

    @PostMapping(path = "save")
    public ResVo<String> save(@RequestBody AiKnowledgeDocReq req, HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        aiKnowledgeAdminService.save(req);
        return ResVo.ok("ok");
    }

    @PostMapping(path = "list")
    public ResVo<PageVo<AiKnowledgeDocDTO>> list(@RequestBody SearchAiKnowledgeDocReq req,
                                                 HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        return ResVo.ok(aiKnowledgeAdminService.list(req));
    }

    @GetMapping(path = "delete")
    public ResVo<String> delete(@RequestParam("id") Long id, HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        aiKnowledgeAdminService.delete(id);
        return ResVo.ok("ok");
    }

    @GetMapping(path = "export")
    public ResVo<List<AiKnowledgeDocDTO>> export(HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        return ResVo.ok(aiKnowledgeAdminService.exportDocs());
    }

    @GetMapping(path = "sync")
    public ResVo<String> sync(@RequestParam("id") Long id, HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        aiKnowledgeAdminService.sync(id);
        return ResVo.ok("ok");
    }

    @GetMapping(path = "syncAll")
    public ResVo<String> syncAll(HttpServletRequest request) {
        aiInternalAccessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        aiKnowledgeAdminService.syncAll();
        return ResVo.ok("ok");
    }
}
