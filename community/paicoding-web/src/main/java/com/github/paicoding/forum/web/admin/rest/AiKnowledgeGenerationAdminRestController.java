package com.github.paicoding.forum.web.admin.rest;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeGenerationRebuildTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Generation 全量重建的管理员异步入口。 */
@RestController
@RequiredArgsConstructor
@Permission(role = UserRole.ADMIN)
@RequestMapping("api/admin/ai/knowledge/generations")
@ConditionalOnProperty(name = "ai.knowledge.generation-rebuild.enabled", havingValue = "true")
public class AiKnowledgeGenerationAdminRestController {
    private final ArticleKnowledgeGenerationRebuildTaskService taskService;

    @PostMapping("rebuild")
    public ResVo<ArticleKnowledgeGenerationRebuildTaskService.TaskSnapshot> rebuild(
            @RequestParam("generationLabel") String generationLabel) {
        return ResVo.ok(taskService.submit(generationLabel));
    }

    @GetMapping("status")
    public ResVo<ArticleKnowledgeGenerationRebuildTaskService.TaskSnapshot> status() {
        return ResVo.ok(taskService.status());
    }
}
