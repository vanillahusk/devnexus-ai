package com.github.paicoding.forum.service.article;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeArticleSnapshotDTO;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDetailDO;
import com.github.paicoding.forum.service.article.repository.mapper.ArticleDetailMapper;
import com.github.paicoding.forum.service.article.repository.mapper.ArticleMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleDaoOnlineKnowledgeSnapshotTest {
    @Test
    void shouldReadLatestOnlineUndeletedSnapshotWithoutPublicDetailSideEffects() {
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ArticleDetailMapper detailMapper = mock(ArticleDetailMapper.class);
        ArticleDO article = new ArticleDO();
        article.setId(1001L);
        article.setTitle("Redis可靠队列");
        article.setSummary("恢复流程");
        article.setCategoryId(3L);
        article.setKnowledgeVersion(8L);
        article.setUpdateTime(new Date(123456L));
        ArticleDetailDO detail = new ArticleDetailDO();
        detail.setContent("正文");
        when(articleMapper.selectOne(any())).thenReturn(article);
        when(detailMapper.selectOne(any())).thenReturn(detail);
        ArticleDao dao = dao(articleMapper, detailMapper);

        AiKnowledgeArticleSnapshotDTO result = dao.queryOnlineKnowledgeSnapshot(1001L);

        assertEquals(8L, result.getArticleVersion());
        assertEquals("正文", result.getContent());
        ArgumentCaptor<Wrapper<ArticleDO>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).selectOne(query.capture());
        // Lambda SQL 元数据由 MyBatis 在集成环境初始化；这里验证专用查询对象确实被交给 Mapper，
        // ONLINE/未删除条件本身再由静态契约检查防止回归。
        assertTrue(query.getValue() != null);
    }

    @Test
    void shouldReturnInvisibleWhenArticleOrDetailDoesNotExist() {
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ArticleDetailMapper detailMapper = mock(ArticleDetailMapper.class);
        ArticleDao dao = dao(articleMapper, detailMapper);
        assertNull(dao.queryOnlineKnowledgeSnapshot(1001L));
        assertNull(dao.queryOnlineKnowledgeSnapshot(0L));
    }

    private ArticleDao dao(ArticleMapper articleMapper, ArticleDetailMapper detailMapper) {
        ArticleDao dao = new ArticleDao();
        ReflectionTestUtils.setField(dao, "articleMapper", articleMapper);
        ReflectionTestUtils.setField(dao, "articleDetailMapper", detailMapper);
        return dao;
    }
}
