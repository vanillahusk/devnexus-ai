package com.github.paicoding.forum.service.ai.repository.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexState;
import com.github.paicoding.forum.service.ai.repository.entity.ArticleKnowledgeIndexStateDO;
import com.github.paicoding.forum.service.ai.repository.mapper.ArticleKnowledgeIndexStateMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public class ArticleKnowledgeIndexStateDao
        extends ServiceImpl<ArticleKnowledgeIndexStateMapper, ArticleKnowledgeIndexStateDO> {

    public ArticleKnowledgeIndexState findState(Long articleId) {
        ArticleKnowledgeIndexStateDO value = getOne(Wrappers.<ArticleKnowledgeIndexStateDO>lambdaQuery()
                .eq(ArticleKnowledgeIndexStateDO::getArticleId, articleId), false);
        if (value == null) {
            return null;
        }
        return new ArticleKnowledgeIndexState(
                value.getArticleId(), value.getArticleVersion(),
                com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum.valueOf(value.getOperation()),
                value.getEventId(), value.getSyncedAt() == null ? null : value.getSyncedAt().getTime());
    }

    public void saveApplied(ArticleKnowledgeEvent event) {
        ArticleKnowledgeIndexStateDO value = getOne(Wrappers.<ArticleKnowledgeIndexStateDO>lambdaQuery()
                .eq(ArticleKnowledgeIndexStateDO::getArticleId, event.getArticleId()), false);
        if (value == null) {
            value = new ArticleKnowledgeIndexStateDO();
            value.setArticleId(event.getArticleId());
        }
        value.setArticleVersion(event.getArticleVersion());
        value.setOperation(event.getOperation().name());
        value.setEventId(event.getEventId());
        value.setIdempotencyKey(event.idempotencyKey());
        value.setSyncedAt(new Date());
        saveOrUpdate(value);
    }
}
