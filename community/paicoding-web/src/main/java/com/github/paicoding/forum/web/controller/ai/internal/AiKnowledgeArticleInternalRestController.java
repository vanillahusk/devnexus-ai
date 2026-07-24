package com.github.paicoding.forum.web.controller.ai.internal;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeArticleSnapshotDTO;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeArticleReadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供受控 AI 工具调用的内部只读文章接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "internal/aigc/knowledge/articles")
public class AiKnowledgeArticleInternalRestController {
    private final AiKnowledgeProperties properties;
    private final AiInternalAccessValidator accessValidator;
    private final AiKnowledgeArticleReadService articleReadService;

    @GetMapping(path = "{articleId}")
    public ResponseEntity<ResVo<AiKnowledgeArticleSnapshotDTO>> detail(@PathVariable Long articleId,
                                                                       HttpServletRequest request) {
        accessValidator.validate(request.getHeader(properties.getService().getTokenHeader()));
        AiKnowledgeArticleSnapshotDTO snapshot = articleReadService.queryOnlineSnapshot(articleId);
        if (snapshot == null) {
            // OFFLINE、REVIEW、已删除、不存在和正文缺失统一表现为不可见，避免状态枚举泄漏。
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ResVo.ok(snapshot));
    }
}
