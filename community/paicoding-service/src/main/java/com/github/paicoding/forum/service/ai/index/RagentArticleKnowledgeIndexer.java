package com.github.paicoding.forum.service.ai.index;

import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.api.model.vo.article.dto.ArticleDTO;
import com.github.paicoding.forum.service.ai.service.RagentKnowledgeSyncService;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagentArticleKnowledgeIndexer implements ArticleKnowledgeIndexer {
    private final ArticleDao articleDao;
    private final RagentKnowledgeSyncService ragentKnowledgeSyncService;
    private final ArticleMarkdownSanitizer markdownSanitizer;

    @Override
    public ApplyResult converge(Long articleId, Long eventVersion,
                                ArticleKnowledgeOperationEnum eventOperation) {
        return converge(articleId, eventVersion, eventOperation, null);
    }

    /** 全量快照和水位重放专用，仅收敛指定物理 Generation。 */
    public ApplyResult convergeToGeneration(Long articleId, Long eventVersion,
                                            ArticleKnowledgeOperationEnum eventOperation,
                                            String physicalCollection) {
        if (physicalCollection == null || physicalCollection.isBlank()) {
            throw new IllegalArgumentException("physicalCollection must not be blank");
        }
        return converge(articleId, eventVersion, eventOperation, physicalCollection);
    }

    private ApplyResult converge(Long articleId, Long eventVersion,
                                 ArticleKnowledgeOperationEnum eventOperation,
                                 String physicalCollection) {
        ArticleDO article = articleDao.getById(articleId);
        if (article == null) {
            remove(articleId, physicalCollection);
            return new ApplyResult(eventVersion, ArticleKnowledgeOperationEnum.OFFLINE);
        }
        Long factVersion = article.getKnowledgeVersion();
        if (factVersion == null || factVersion < eventVersion) {
            throw new IllegalStateException("article fact version is behind event, articleId=" + articleId
                    + ", eventVersion=" + eventVersion + ", factVersion=" + factVersion);
        }
        boolean online = Objects.equals(article.getStatus(), PushStatusEnum.ONLINE.getCode())
                && !Objects.equals(article.getDeleted(), YesOrNoEnum.YES.getCode());
        if (!online) {
            remove(articleId, physicalCollection);
            return new ApplyResult(factVersion, ArticleKnowledgeOperationEnum.OFFLINE);
        }
        ArticleDTO detail = articleDao.queryArticleDetail(articleId);
        if (detail == null || detail.getContent() == null || detail.getContent().isBlank()) {
            throw new IllegalStateException("article content not found, articleId=" + articleId);
        }

        String cleanedMarkdown = markdownSanitizer.sanitize(detail.getContent());
        if (cleanedMarkdown.isBlank()) {
            throw new IllegalStateException("article content is empty after sanitizing, articleId=" + articleId);
        }
        AiKnowledgeDocDTO doc = new AiKnowledgeDocDTO();
        doc.setId(articleId);
        doc.setType("ARTICLE");
        doc.setCode("article-" + articleId + "-v" + factVersion);
        doc.setKey(mappingKey(articleId));
        String title = Objects.toString(article.getTitle(), "未命名文章")
                .replace('\n', ' ').replace('\r', ' ').trim();
        doc.setTitle(title);
        doc.setContent(cleanedMarkdown);
        StringBuilder frontMatter = new StringBuilder("---\nsourceType: ARTICLE\narticleId: ")
                .append(articleId).append("\narticleVersion: ").append(factVersion)
                .append("\nstatus: ONLINE\ntitle: ").append(title);
        if (detail.getCategory() != null && detail.getCategory().getCategoryId() != null) {
            frontMatter.append("\ncategoryId: ").append(detail.getCategory().getCategoryId());
        }
        if (detail.getTags() != null && !detail.getTags().isEmpty()) {
            String tagIds = detail.getTags().stream()
                    .map(tag -> tag.getTagId() == null ? "" : tag.getTagId().toString())
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining(","));
            if (!tagIds.isBlank()) frontMatter.append("\ntagIds: ").append(tagIds);
        }
        doc.setExportMarkdown(frontMatter + "\n---\n# " + title + "\n\n" + cleanedMarkdown);
        if (physicalCollection == null) {
            ragentKnowledgeSyncService.sync(doc);
        } else {
            ragentKnowledgeSyncService.syncToGeneration(doc, physicalCollection);
        }
        ArticleKnowledgeOperationEnum appliedOperation = factVersion.equals(eventVersion)
                && eventOperation != ArticleKnowledgeOperationEnum.OFFLINE
                ? eventOperation : ArticleKnowledgeOperationEnum.UPDATE;
        return new ApplyResult(factVersion, appliedOperation);
    }

    private void remove(Long articleId, String physicalCollection) {
        if (physicalCollection == null) {
            ragentKnowledgeSyncService.deleteStrictByConfigKey(mappingKey(articleId));
        } else {
            ragentKnowledgeSyncService.deleteFromGeneration(mappingKey(articleId), physicalCollection);
        }
    }

    private String mappingKey(Long articleId) {
        return "article:" + articleId;
    }
}
