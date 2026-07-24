package com.github.paicoding.forum.service.ai.service.impl;

import com.github.paicoding.forum.api.model.vo.ai.AiKnowledgeArticleSnapshotDTO;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeArticleReadService;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 不经过公开文章详情缓存，也不写阅读计数和用户足迹。 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeArticleReadServiceImpl implements AiKnowledgeArticleReadService {
    private final ArticleDao articleDao;

    @Override
    public AiKnowledgeArticleSnapshotDTO queryOnlineSnapshot(Long articleId) {
        return articleDao.queryOnlineKnowledgeSnapshot(articleId);
    }
}
