package com.github.paicoding.forum.web.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.vo.comment.CommentSaveReq;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.notify.service.DlqReplayAuditService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RocketMqDeadLetterReplayService {
    private final MessageQueueService messageQueueService;
    private final ObjectMapper objectMapper;
    private final DlqReplayAuditService auditService;

    public ReplayResult replayCorrected(ReplayRequest request, Long operatorId) {
        validate(request);
        Object content = convertContent(request);
        MessageQueueEvent<Object> event = new MessageQueueEvent<>();
        event.setEventId(UUID.randomUUID().toString());
        event.setOriginalEventId(request.getEventId());
        event.setEventVersion(MessageQueueEvent.CURRENT_EVENT_VERSION);
        event.setOccurredAt(System.currentTimeMillis());
        event.setNotifyType(request.getNotifyType());
        event.setUserId(request.getUserId());
        event.setContent(content);

        auditService.begin(new DlqReplayAuditService.ReplayAuditCommand(
                request.getOriginalMsgId(), request.getEventId(), event.getEventId(),
                CommonConstants.ROCKETMQ_TOPIC_BUSINESS_EVENT, request.getTag(),
                businessKey(request, content), request.getReason(), operatorId));
        try {
            messageQueueService.publish(event, request.getTag());
            auditService.markSubmitted(event.getEventId());
        } catch (RuntimeException failure) {
            markAuditFailed(event.getEventId(), failure);
            throw failure;
        }
        log.warn("RocketMQ DLQ correction submitted, originalMsgId={}, originalEventId={}, "
                        + "correctionEventId={}, tag={}, reason={}",
                request.getOriginalMsgId(), request.getEventId(), event.getEventId(),
                request.getTag(), request.getReason());
        return new ReplayResult(request.getOriginalMsgId(), event.getEventId(), request.getTag(), true);
    }

    public ReplayResult replayCorrectedArticleKnowledge(ArticleKnowledgeReplayRequest request, Long operatorId) {
        validateArticleKnowledge(request);
        ArticleKnowledgeEvent event = ArticleKnowledgeEvent.create(
                request.getArticleId(), request.getArticleVersion(), request.getOperation());
        event.setOriginalEventId(request.getEventId());
        auditService.begin(new DlqReplayAuditService.ReplayAuditCommand(
                request.getOriginalMsgId(), request.getEventId(), event.getEventId(),
                CommonConstants.ROCKETMQ_TOPIC_ARTICLE_KNOWLEDGE,
                CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1,
                "article:" + request.getArticleId(), request.getReason(), operatorId));
        try {
            messageQueueService.publish(CommonConstants.ROCKETMQ_TOPIC_ARTICLE_KNOWLEDGE,
                    CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1, event, event.getEventId());
            auditService.markSubmitted(event.getEventId());
        } catch (RuntimeException failure) {
            markAuditFailed(event.getEventId(), failure);
            throw failure;
        }
        log.warn("article knowledge DLQ correction submitted, originalMsgId={}, originalEventId={}, eventId={}, "
                        + "articleId={}, version={}, operation={}, reason={}",
                request.getOriginalMsgId(), request.getEventId(), event.getEventId(), request.getArticleId(),
                request.getArticleVersion(), request.getOperation(), request.getReason());
        return new ReplayResult(request.getOriginalMsgId(), event.getEventId(),
                CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1, true);
    }

    private Object convertContent(ReplayRequest request) {
        if (CommonConstants.ROCKETMQ_TAG_COMMENT_WRITE.equals(request.getTag())) {
            return objectMapper.convertValue(request.getContent(), CommentSaveReq.class);
        }
        return request.getContent();
    }

    private String businessKey(ReplayRequest request, Object content) {
        if (content instanceof CommentSaveReq comment) {
            return "comment:article:" + comment.getArticleId();
        }
        return request.getUserId() == null ? null : "notify:user:" + request.getUserId();
    }

    private void markAuditFailed(String eventId, RuntimeException failure) {
        try {
            auditService.markFailed(eventId, failure);
        } catch (RuntimeException auditFailure) {
            failure.addSuppressed(auditFailure);
            log.error("failed to mark DLQ replay audit as FAILED, correctionEventId={}", eventId, auditFailure);
        }
    }

    private void validate(ReplayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("replay request must not be null");
        }
        requireText(request.getOriginalMsgId(), "originalMsgId");
        requireText(request.getEventId(), "eventId");
        requireText(request.getReason(), "reason");
        if (!CommonConstants.ROCKETMQ_TAG_COMMENT_WRITE.equals(request.getTag())
                && !CommonConstants.ROCKETMQ_TAG_NOTIFY.equals(request.getTag())) {
            throw new IllegalArgumentException("unsupported RocketMQ tag: " + request.getTag());
        }
        if (request.getNotifyType() == null || request.getContent() == null || request.getContent().isNull()) {
            throw new IllegalArgumentException("notifyType/content must not be null");
        }
    }

    private void validateArticleKnowledge(ArticleKnowledgeReplayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("article knowledge replay request must not be null");
        }
        requireText(request.getOriginalMsgId(), "originalMsgId");
        requireText(request.getEventId(), "eventId");
        requireText(request.getReason(), "reason");
        if (request.getArticleId() == null || request.getArticleId() <= 0) {
            throw new IllegalArgumentException("articleId must be positive");
        }
        if (request.getArticleVersion() == null || request.getArticleVersion() <= 0) {
            throw new IllegalArgumentException("articleVersion must be positive");
        }
        if (request.getOperation() == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    @Data
    public static class ReplayRequest {
        private String originalMsgId;
        private String eventId;
        private String tag;
        private NotifyTypeEnum notifyType;
        private Long userId;
        private JsonNode content;
        private String reason;
    }

    @Data
    public static class ArticleKnowledgeReplayRequest {
        private String originalMsgId;
        private String eventId;
        private Long articleId;
        private Long articleVersion;
        private ArticleKnowledgeOperationEnum operation;
        private String reason;
    }

    public record ReplayResult(String originalMsgId, String eventId, String tag, boolean submitted) {
    }
}
