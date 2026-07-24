package com.github.paicoding.forum.service.ai.index;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.service.ai.repository.dao.ArticleKnowledgeIndexStateDao;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** MySQL 文章事实状态与已应用索引版本的增量对账。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.knowledge.index.reconciliation.enabled", havingValue = "true")
public class ArticleKnowledgeReconciliationService {
    private final ArticleDao articleDao;
    private final ArticleKnowledgeIndexStateDao stateDao;
    private final MqOutboxService outboxService;

    @Scheduled(fixedDelayString = "${ai.knowledge.index.reconciliation.delay-ms:300000}")
    public void scheduledReconcile() {
        long lastId = 0L;
        int repaired = 0;
        while (true) {
            ReconciliationResult result = reconcileBatch(lastId, 100);
            repaired += result.repairEvents();
            if (!result.hasMore()) {
                break;
            }
            lastId = result.lastArticleId();
        }
        if (repaired > 0) {
            log.warn("article knowledge reconciliation submitted repair events, count={}", repaired);
        }
    }

    public ReconciliationResult reconcileBatch(long lastId, int requestedSize) {
        int size = Math.max(1, Math.min(requestedSize, 500));
        List<ArticleDO> articles = articleDao.list(Wrappers.<ArticleDO>lambdaQuery()
                .gt(ArticleDO::getId, lastId)
                .gt(ArticleDO::getKnowledgeVersion, 0L)
                .orderByAsc(ArticleDO::getId)
                .last("LIMIT " + size));
        int repairEvents = 0;
        long nextLastId = lastId;
        for (ArticleDO article : articles) {
            nextLastId = article.getId();
            ArticleKnowledgeIndexState state = stateDao.findState(article.getId());
            ArticleKnowledgeOperationEnum expected = expectedOperation(article, state);
            if (isConsistent(article, state, expected)) {
                continue;
            }
            ArticleKnowledgeEvent repair = ArticleKnowledgeEvent.create(
                    article.getId(), article.getKnowledgeVersion(), expected);
            repair.setEventId(repairEventId(article.getId(), article.getKnowledgeVersion(), expected));
            outboxService.saveArticleKnowledge(repair);
            repairEvents++;
        }
        return new ReconciliationResult(articles.size(), repairEvents, nextLastId, articles.size() == size);
    }

    private boolean isConsistent(ArticleDO article, ArticleKnowledgeIndexState state,
                                 ArticleKnowledgeOperationEnum expected) {
        return state != null
                && Objects.equals(state.articleVersion(), article.getKnowledgeVersion())
                && state.operation() == expected;
    }

    private ArticleKnowledgeOperationEnum expectedOperation(ArticleDO article,
                                                             ArticleKnowledgeIndexState state) {
        boolean online = Objects.equals(article.getStatus(), PushStatusEnum.ONLINE.getCode())
                && !Objects.equals(article.getDeleted(), YesOrNoEnum.YES.getCode());
        if (!online) {
            return ArticleKnowledgeOperationEnum.OFFLINE;
        }
        return state == null ? ArticleKnowledgeOperationEnum.ONLINE : ArticleKnowledgeOperationEnum.UPDATE;
    }

    static String repairEventId(Long articleId, Long version, ArticleKnowledgeOperationEnum operation) {
        return "article-reconcile:" + articleId + ":" + version + ":" + operation.name();
    }

    public record ReconciliationResult(int scanned, int repairEvents, long lastArticleId, boolean hasMore) {
    }
}
