package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReferenceDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;
import com.github.paicoding.forum.api.model.vo.article.dto.ArticleDTO;
import com.github.paicoding.forum.api.model.vo.comment.dto.TopCommentDTO;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiExternalCallGuard;
import com.github.paicoding.forum.service.ai.service.AiRequestGovernanceService;
import com.github.paicoding.forum.service.ai.service.impl.AiKnowledgeAssistantServiceImpl;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.service.comment.service.CommentReadService;
import com.github.paicoding.forum.service.config.repository.dao.ConfigDao;
import com.github.paicoding.forum.service.config.repository.entity.GlobalConfigDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKnowledgeAssistantRecallBaselineTest {
    private ArticleReadService articleReadService;
    private CommentReadService commentReadService;
    private ConfigDao configDao;
    private StringRedisTemplate redisTemplate;
    private AiKnowledgeAssistantServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        articleReadService = mock(ArticleReadService.class);
        commentReadService = mock(CommentReadService.class);
        configDao = mock(ConfigDao.class);
        redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(any(), anyLong(), anyLong())).thenReturn(Collections.emptyList());
        when(configDao.listGlobalConfig(any())).thenReturn(Collections.emptyList());
        when(articleReadService.queryArticlesBySearchKey(any(), any())).thenReturn(PageListVo.emptyVo());

        AiKnowledgeProperties properties = new AiKnowledgeProperties();
        properties.getRoute().setPrimary("local");
        service = new AiKnowledgeAssistantServiceImpl(
                articleReadService,
                commentReadService,
                configDao,
                redisTemplate,
                properties,
                mock(AiExternalCallGuard.class),
                mock(AiRequestGovernanceService.class));
    }

    @Test
    void shouldRecallCurrentArticleAndDeduplicateSameComment() {
        ArticleDTO current = article(101L, "RocketMQ可靠消息", "Outbox摘要", "Outbox与消费幂等正文");
        TopCommentDTO comment = comment(9001L, "补充说明消费幂等和重复投递");
        when(articleReadService.queryDetailArticleInfo(101L)).thenReturn(current);
        when(commentReadService.getArticleComments(anyLong(), any())).thenReturn(List.of(comment));
        when(commentReadService.queryHotComment(101L)).thenReturn(comment);

        AiAssistantReplyDTO reply = service.ask(request("RocketMQ 幂等", 101L), 7L);

        assertEquals("article", reply.getReferences().get(0).getSourceType());
        assertEquals(101L, reply.getReferences().get(0).getArticleId());
        assertEquals(1L, reply.getReferences().stream()
                .filter(reference -> Long.valueOf(9001L).equals(reference.getCommentId()))
                .count());
    }

    @Test
    void shouldUseTruncatedQuestionForLegacyMysqlSearchThenLoadArticleDetail() {
        String question = "RocketMQ消息积压后如何恢复？还要验证重复消费与最终一致性，这是超过三十二字符的尾部";
        ArticleDTO hit = article(202L, "消息积压恢复", "恢复摘要", null);
        ArticleDTO detail = article(202L, "消息积压恢复", "恢复摘要", "Broker恢复后消费积压");
        when(articleReadService.queryArticlesBySearchKey(any(), any()))
                .thenReturn(PageListVo.newVo(List.of(hit), 3));
        when(articleReadService.queryDetailArticleInfo(202L)).thenReturn(detail);

        AiAssistantReplyDTO reply = service.ask(request(question, null), 7L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(articleReadService).queryArticlesBySearchKey(keyCaptor.capture(), any());
        assertEquals(32, keyCaptor.getValue().length());
        assertTrue(reply.getReferences().stream()
                .anyMatch(reference -> Long.valueOf(202L).equals(reference.getArticleId())));
    }

    @Test
    void shouldMergeRuleFaqAndArticleCandidatesThenLimitReferencesToSix() {
        List<GlobalConfigDO> configs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            GlobalConfigDO config = new GlobalConfigDO();
            config.setId((long) i + 1);
            config.setKey(i % 2 == 0 ? "ai.knowledge.rule.rule-" + i : "ai.knowledge.faq.faq-" + i);
            config.setComment("RocketMQ规则" + i);
            config.setValue("RocketMQ失败重试与恢复说明" + i);
            configs.add(config);
        }
        when(configDao.listGlobalConfig(any())).thenReturn(configs);
        ArticleDTO hit = article(303L, "RocketMQ文章", "重试摘要", null);
        when(articleReadService.queryArticlesBySearchKey(any(), any()))
                .thenReturn(PageListVo.newVo(List.of(hit), 3));
        when(articleReadService.queryDetailArticleInfo(303L))
                .thenReturn(article(303L, "RocketMQ文章", "重试摘要", "重试正文"));

        AiAssistantReplyDTO reply = service.ask(request("RocketMQ 重试", null), 7L);

        assertEquals(6, reply.getReferences().size());
        assertTrue(reply.getReferences().stream().map(AiAssistantReferenceDTO::getSourceType)
                .anyMatch(type -> "rule".equals(type) || "faq".equals(type)));
    }

    private AiAssistantAskReq request(String question, Long articleId) {
        AiAssistantAskReq request = new AiAssistantAskReq();
        request.setQuestion(question);
        request.setArticleId(articleId);
        request.setIncludeComments(true);
        return request;
    }

    private ArticleDTO article(Long id, String title, String summary, String content) {
        ArticleDTO article = new ArticleDTO();
        article.setArticleId(id);
        article.setTitle(title);
        article.setSummary(summary);
        article.setContent(content);
        return article;
    }

    private TopCommentDTO comment(Long id, String content) {
        TopCommentDTO comment = new TopCommentDTO();
        comment.setCommentId(id);
        comment.setUserName("测试用户");
        comment.setCommentContent(content);
        comment.setCommentTime(1L);
        return comment;
    }
}
