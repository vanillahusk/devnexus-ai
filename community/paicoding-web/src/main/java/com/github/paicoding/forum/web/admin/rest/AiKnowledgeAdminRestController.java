package com.github.paicoding.forum.web.admin.rest;

import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.SearchAiKnowledgeDocReq;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.service.ai.facade.AiKnowledgeAdminFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 知识库后台管理
 *
 * @author Codex
 * @date 2026-04-01
 */
@RestController
@RequiredArgsConstructor
@Permission(role = UserRole.ADMIN)
@RequestMapping(path = {"api/admin/ai/knowledge/", "admin/ai/knowledge/"})
public class AiKnowledgeAdminRestController {

    private final AiKnowledgeAdminFacade aiKnowledgeAdminFacade;

    @PostMapping(path = "save")
    public ResVo<String> save(@RequestBody AiKnowledgeDocReq req) {
        aiKnowledgeAdminFacade.save(req);
        return ResVo.ok("ok");
    }

    @PostMapping(path = "list")
    public ResVo<PageVo<AiKnowledgeDocDTO>> list(@RequestBody SearchAiKnowledgeDocReq req) {
        return ResVo.ok(aiKnowledgeAdminFacade.list(req));
    }

    @GetMapping(path = "delete")
    public ResVo<String> delete(@RequestParam("id") Long id) {
        aiKnowledgeAdminFacade.delete(id);
        return ResVo.ok("ok");
    }

    @GetMapping(path = "export")
    public ResVo<List<AiKnowledgeDocDTO>> export() {
        return ResVo.ok(aiKnowledgeAdminFacade.exportDocs());
    }

    @GetMapping(path = "sync")
    public ResVo<String> sync(@RequestParam("id") Long id) {
        aiKnowledgeAdminFacade.sync(id);
        return ResVo.ok("ok");
    }

    @GetMapping(path = "syncAll")
    public ResVo<String> syncAll() {
        aiKnowledgeAdminFacade.syncAll();
        return ResVo.ok("ok");
    }
}
