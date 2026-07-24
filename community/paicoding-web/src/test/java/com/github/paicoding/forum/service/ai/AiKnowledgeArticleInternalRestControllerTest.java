package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeArticleSnapshotDTO;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeArticleReadService;
import com.github.paicoding.forum.web.controller.ai.internal.AiInternalAccessValidator;
import com.github.paicoding.forum.web.controller.ai.internal.AiKnowledgeArticleInternalRestController;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiKnowledgeArticleInternalRestControllerTest {
    @Test
    void shouldValidateInternalTokenBeforeReturningOnlineSnapshot() {
        AiKnowledgeProperties properties = new AiKnowledgeProperties();
        properties.getService().setTokenHeader("X-INTERNAL");
        AiInternalAccessValidator validator = mock(AiInternalAccessValidator.class);
        AiKnowledgeArticleReadService readService = mock(AiKnowledgeArticleReadService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-INTERNAL")).thenReturn("secret");
        when(readService.queryOnlineSnapshot(1001L)).thenReturn(AiKnowledgeArticleSnapshotDTO.builder()
                .articleId(1001L).articleVersion(8L).title("title").content("content").build());
        AiKnowledgeArticleInternalRestController controller =
                new AiKnowledgeArticleInternalRestController(properties, validator, readService);

        var response = controller.detail(1001L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var order = inOrder(validator, readService);
        order.verify(validator).validate("secret");
        order.verify(readService).queryOnlineSnapshot(1001L);
    }

    @Test
    void shouldUseSameNotFoundResultForEveryInvisibleState() {
        AiKnowledgeProperties properties = new AiKnowledgeProperties();
        AiInternalAccessValidator validator = mock(AiInternalAccessValidator.class);
        AiKnowledgeArticleReadService readService = mock(AiKnowledgeArticleReadService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AiKnowledgeArticleInternalRestController controller =
                new AiKnowledgeArticleInternalRestController(properties, validator, readService);

        assertEquals(HttpStatus.NOT_FOUND, controller.detail(1001L, request).getStatusCode());
    }
}
