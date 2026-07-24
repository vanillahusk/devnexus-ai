package com.github.paicoding.forum.service.ai.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.PageParam;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantHistoryItemDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReferenceDTO;
import com.github.paicoding.forum.api.model.vo.ai.AiAssistantReplyDTO;
import com.github.paicoding.forum.api.model.vo.comment.dto.TopCommentDTO;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.core.net.HttpRequestHelper;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiKnowledgeAssistantService;
import com.github.paicoding.forum.service.ai.service.AiExternalCallGuard;
import com.github.paicoding.forum.service.ai.service.AiRequestGovernanceService;
import com.github.paicoding.forum.service.ai.retrieval.LegacyRetrievalPolicy;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.service.comment.service.CommentReadService;
import com.github.paicoding.forum.service.config.repository.dao.ConfigDao;
import com.github.paicoding.forum.service.config.repository.entity.GlobalConfigDO;
import com.github.paicoding.forum.service.config.repository.params.SearchGlobalConfigParams;
import com.github.paicoding.forum.api.model.vo.article.dto.ArticleDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 知识助手实现
 *
 * 核心链路：
 * 1. 从社区规则/FAQ、文章、评论中本地召回上下文
 * 2. 优先按路由把上下文交给 ragent 或直连模型 API 生成
 * 3. 如果外部路由不可用，则直接本地模板降级回答
 * 4. 会话历史落 Redis，方便连续追问
 *
 * @author Codex
 * @date 2026-04-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeAssistantServiceImpl implements AiKnowledgeAssistantService {
    private static final String HISTORY_KEY_PREFIX = "ai:knowledge:history:";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleReadService articleReadService;
    private final CommentReadService commentReadService;
    private final ConfigDao configDao;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiKnowledgeProperties properties;
    private final AiExternalCallGuard externalCallGuard;
    private final AiRequestGovernanceService requestGovernanceService;

    @Override
    public AiAssistantReplyDTO ask(AiAssistantAskReq req, Long userId) {
        if (req == null || StringUtils.isBlank(req.getQuestion())) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "问题不能为空");
        }
        if (userId == null) {
            throw ExceptionUtil.of(StatusEnum.FORBID_NOTLOGIN);
        }
        requestGovernanceService.check(userId, req.getQuestion());

        String sessionId = StringUtils.isBlank(req.getSessionId()) ? generateSessionId(userId) : req.getSessionId().trim();
        List<AiAssistantHistoryItemDTO> history = history(sessionId, userId);
        List<KnowledgeChunk> recalledChunks = recallKnowledge(req);
        List<AiAssistantReferenceDTO> references = recalledChunks.stream()
                .map(KnowledgeChunk::toReference)
                .limit(6)
                .collect(Collectors.toList());

        String answer;
        String route = "local";
        boolean degraded = true;
        String degradeReason = "未启用外部 AI 路由，直接走本地知识模板";

        if (shouldUseRagent()) {
            String enrichedPrompt = buildRagentPrompt(req.getQuestion(), recalledChunks, history);
            try {
                String ragentAnswer = externalCallGuard.execute("ragent",
                        () -> callRagent(enrichedPrompt, sessionId, Boolean.TRUE.equals(req.getDeepThinking())));
                if (StringUtils.isNotBlank(ragentAnswer)) {
                    answer = ragentAnswer;
                    route = "ragent";
                    degraded = false;
                    degradeReason = null;
                } else {
                    answer = buildLocalFallbackAnswer(req.getQuestion(), recalledChunks);
                    degradeReason = "ragent 返回为空，已降级到本地模板";
                }
            } catch (Exception e) {
                log.warn("AI知识助手调用 ragent 失败, sessionId:{}, questionLength:{}, errorType:{}",
                        sessionId, req.getQuestion().length(), e.getClass().getSimpleName());
                answer = buildLocalFallbackAnswer(req.getQuestion(), recalledChunks);
                degradeReason = StringUtils.defaultIfBlank(e.getMessage(), "ragent 调用失败，已降级到本地模板");
            }
        } else if (shouldUseApi()) {
            try {
                String apiAnswer = externalCallGuard.execute("api",
                        () -> callModelApi(req.getQuestion(), recalledChunks, history,
                                Boolean.TRUE.equals(req.getDeepThinking())));
                if (StringUtils.isNotBlank(apiAnswer)) {
                    answer = apiAnswer;
                    route = "api";
                    degraded = false;
                    degradeReason = null;
                } else {
                    answer = buildLocalFallbackAnswer(req.getQuestion(), recalledChunks);
                    degradeReason = "模型 API 返回为空，已降级到本地模板";
                }
            } catch (Exception e) {
                log.warn("AI知识助手调用模型 API 失败, sessionId:{}, questionLength:{}, errorType:{}",
                        sessionId, req.getQuestion().length(), e.getClass().getSimpleName());
                answer = buildLocalFallbackAnswer(req.getQuestion(), recalledChunks);
                degradeReason = StringUtils.defaultIfBlank(e.getMessage(), "模型 API 调用失败，已降级到本地模板");
            }
        } else {
            answer = buildLocalFallbackAnswer(req.getQuestion(), recalledChunks);
        }

        AiAssistantHistoryItemDTO item = new AiAssistantHistoryItemDTO();
        item.setQuestion(req.getQuestion().trim());
        item.setAnswer(answer);
        item.setAskTime(TIME_FORMATTER.format(LocalDateTime.now()));
        item.setRoute(route);
        item.setDegraded(degraded);
        saveHistory(sessionId, userId, item);

        AiAssistantReplyDTO reply = new AiAssistantReplyDTO();
        reply.setArticleId(req.getArticleId());
        reply.setSessionId(sessionId);
        reply.setAnswer(answer);
        reply.setRoute(route);
        reply.setDegraded(degraded);
        reply.setDegradeReason(degradeReason);
        reply.setReferences(references);
        reply.setHistory(history(sessionId, userId));
        return reply;
    }

    @Override
    public List<AiAssistantHistoryItemDTO> history(String sessionId, Long userId) {
        if (StringUtils.isBlank(sessionId) || userId == null) {
            return Collections.emptyList();
        }
        List<String> raws = stringRedisTemplate.opsForList().range(historyRedisKey(userId, sessionId), 0, properties.getMemoryLimit() - 1L);
        if (raws == null || raws.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiAssistantHistoryItemDTO> items = new ArrayList<>(raws.size());
        for (String raw : raws) {
            try {
                items.add(JsonUtil.toObj(raw, AiAssistantHistoryItemDTO.class));
            } catch (Exception e) {
                log.warn("AI知识助手历史解析失败, raw:{}, msg:{}", raw, e.getMessage());
            }
        }
        return items;
    }

    private boolean shouldUseRagent() {
        return properties.getRagent().isEnabled()
                && "ragent".equalsIgnoreCase(properties.getRoute().getPrimary())
                && StringUtils.isNotBlank(properties.getRagent().getBaseUrl());
    }

    private boolean shouldUseApi() {
        return properties.getApi().isEnabled()
                && ("api".equalsIgnoreCase(properties.getRoute().getPrimary())
                || "openai".equalsIgnoreCase(properties.getRoute().getPrimary()))
                && StringUtils.isNotBlank(properties.getApi().getBaseUrl())
                && StringUtils.isNotBlank(properties.getApi().getApiKey())
                && StringUtils.isNotBlank(properties.getApi().getModel());
    }

    private String callRagent(String question, String sessionId, boolean deepThinking) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("question", question);
        params.put("conversationId", sessionId);
        params.put("deepThinking", String.valueOf(deepThinking));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "text/event-stream");
        String url = trimTrailingSlash(properties.getRagent().getBaseUrl()) + properties.getRagent().getChatPath()
                + "?question={question}&conversationId={conversationId}&deepThinking={deepThinking}";
        String response = HttpRequestHelper.fetchContentWithoutProxy(url, HttpMethod.GET, params, headers, String.class,
                properties.getRagent().getConnectTimeoutMs(), properties.getRagent().getReadTimeoutMs());
        return parseRagentSse(response);
    }

    private String callModelApi(String question, List<KnowledgeChunk> chunks,
                                List<AiAssistantHistoryItemDTO> history, boolean deepThinking) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, normalizeBearer(properties.getApi().getApiKey()));
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getApi().getModel());
        body.put("messages", buildApiMessages(question, chunks, history, deepThinking));
        body.put("temperature", properties.getApi().getTemperature());
        body.put("max_tokens", properties.getApi().getMaxTokens());
        body.put("stream", false);

        String url = trimTrailingSlash(properties.getApi().getBaseUrl()) + properties.getApi().getChatPath();
        String response = properties.getApi().isUseProxy()
                ? HttpRequestHelper.fetchByRequestBodyWithProxy(url, body, headers, String.class,
                properties.getApi().getConnectTimeoutMs(), properties.getApi().getReadTimeoutMs())
                : HttpRequestHelper.fetchByRequestBodyWithoutProxy(url, body, headers, String.class,
                properties.getApi().getConnectTimeoutMs(), properties.getApi().getReadTimeoutMs());
        return parseOpenAiCompatibleResponse(response);
    }

    private String parseRagentSse(String response) {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        StringBuilder answer = new StringBuilder();
        String[] lines = response.split("\\r?\\n");
        for (String line : lines) {
            if (!StringUtils.startsWith(line, "data:")) {
                continue;
            }
            String payload = StringUtils.trimToEmpty(StringUtils.substringAfter(line, "data:"));
            if (StringUtils.isBlank(payload) || "\"[DONE]\"".equals(payload) || "[DONE]".equals(payload)) {
                continue;
            }
            if (!StringUtils.startsWith(payload, "{")) {
                continue;
            }
            try {
                RagentMessageDelta delta = JsonUtil.toObj(payload, RagentMessageDelta.class);
                if ("response".equalsIgnoreCase(delta.getType()) && StringUtils.isNotBlank(delta.getDelta())) {
                    answer.append(delta.getDelta());
                }
            } catch (Exception ignored) {
                // meta / finish 等其他事件直接忽略
            }
        }
        return StringUtils.trimToNull(answer.toString());
    }

    private String parseOpenAiCompatibleResponse(String response) {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        JSONObject json = JSONObject.parseObject(response);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return StringUtils.trimToNull(json.getString("output_text"));
        }
        JSONObject first = choices.getJSONObject(0);
        if (first == null) {
            return null;
        }
        JSONObject message = first.getJSONObject("message");
        if (message != null) {
            return normalizeModelContent(message.get("content"));
        }
        return StringUtils.trimToNull(first.getString("text"));
    }

    private String normalizeModelContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String str) {
            return StringUtils.trimToNull(str);
        }
        if (content instanceof JSONArray array) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject obj) {
                    String text = StringUtils.defaultIfBlank(obj.getString("text"), obj.getString("content"));
                    if (StringUtils.isNotBlank(text)) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(text.trim());
                    }
                } else if (item != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(item);
                }
            }
            return StringUtils.trimToNull(sb.toString());
        }
        if (content instanceof JSONObject obj) {
            return StringUtils.trimToNull(StringUtils.defaultIfBlank(obj.getString("text"), obj.getString("content")));
        }
        return StringUtils.trimToNull(String.valueOf(content));
    }

    private List<KnowledgeChunk> recallKnowledge(AiAssistantAskReq req) {
        Map<String, KnowledgeChunk> merged = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : recallConfigKnowledge(req.getQuestion())) {
            merged.putIfAbsent(chunk.getUniqueKey(), chunk);
        }
        for (KnowledgeChunk chunk : recallArticleKnowledge(req)) {
            merged.putIfAbsent(chunk.getUniqueKey(), chunk);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingInt(KnowledgeChunk::getScore).reversed())
                .limit(8)
                .collect(Collectors.toList());
    }

    private List<KnowledgeChunk> recallConfigKnowledge(String question) {
        SearchGlobalConfigParams params = new SearchGlobalConfigParams();
        params.setKey(properties.getConfigPrefix());
        params.setPageNum(1L);
        params.setPageSize(Math.max(properties.getRecallConfigLimit(), 20));
        params.setOffset(0L);
        params.setLimit(params.getPageSize());
        List<GlobalConfigDO> configs = configDao.listGlobalConfig(params);
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }

        return configs.stream()
                .filter(config -> StringUtils.startsWith(config.getKey(), properties.getConfigPrefix()))
                .map(config -> {
                    String suffix = StringUtils.removeStart(config.getKey(), properties.getConfigPrefix());
                    String type = StringUtils.substringBefore(suffix, ".");
                    KnowledgeChunk chunk = new KnowledgeChunk();
                    chunk.setUniqueKey("cfg:" + config.getId());
                    chunk.setSourceType(type);
                    chunk.setTitle(StringUtils.defaultIfBlank(config.getComment(), suffix));
                    chunk.setSnippet(limitSnippet(config.getValue(), 220));
                    chunk.setConfigKey(config.getKey());
                    chunk.setScore(LegacyRetrievalPolicy.score(question, config.getComment(), config.getValue()) + 50);
                    return chunk;
                })
                .sorted(Comparator.comparingInt(KnowledgeChunk::getScore).reversed())
                .limit(properties.getRecallConfigLimit())
                .collect(Collectors.toList());
    }

    private List<KnowledgeChunk> recallArticleKnowledge(AiAssistantAskReq req) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        if (req.getArticleId() != null) {
            try {
                ArticleDTO article = articleReadService.queryDetailArticleInfo(req.getArticleId());
                if (article != null) {
                    chunks.add(buildArticleChunk(article, req.getQuestion(), 80));
                }
            } catch (Exception e) {
                log.warn("AI知识助手加载文章上下文失败, articleId:{}, msg:{}", req.getArticleId(), e.getMessage());
            }

            if (!Boolean.FALSE.equals(req.getIncludeComments())) {
                List<TopCommentDTO> comments = commentReadService.getArticleComments(
                        req.getArticleId(), PageParam.newPageInstance(1L, (long) properties.getRecallCommentLimit()));
                if (comments != null) {
                    for (TopCommentDTO comment : comments) {
                        chunks.add(buildCommentChunk(comment, req.getQuestion(), req.getArticleId(), 55));
                    }
                }

                TopCommentDTO hotComment = commentReadService.queryHotComment(req.getArticleId());
                if (hotComment != null) {
                    chunks.add(buildCommentChunk(hotComment, req.getQuestion(), req.getArticleId(), 60));
                }
            }
        }

        String searchKey = limitSearchKey(req.getQuestion());
        if (StringUtils.isNotBlank(searchKey)) {
            PageListVo<ArticleDTO> page = articleReadService.queryArticlesBySearchKey(
                    searchKey, PageParam.newPageInstance(1L, (long) properties.getRecallArticleLimit()));
            if (page != null && page.getList() != null) {
                for (ArticleDTO article : page.getList()) {
                    if (Objects.equals(article.getArticleId(), req.getArticleId())) {
                        continue;
                    }
                    try {
                        ArticleDTO detail = articleReadService.queryDetailArticleInfo(article.getArticleId());
                        if (detail != null) {
                            chunks.add(buildArticleChunk(detail, req.getQuestion(), 40));
                        }
                    } catch (Exception e) {
                        log.warn("AI知识助手加载搜索文章详情失败, articleId:{}, msg:{}", article.getArticleId(), e.getMessage());
                    }
                }
            }
        }
        return chunks;
    }

    private KnowledgeChunk buildArticleChunk(ArticleDTO article, String question, int baseScore) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setUniqueKey("article:" + article.getArticleId());
        chunk.setSourceType("article");
        chunk.setTitle(article.getTitle());
        chunk.setArticleId(article.getArticleId());
        String body = StringUtils.defaultIfBlank(article.getSummary(), "") + "\n" + StringUtils.defaultIfBlank(article.getContent(), "");
        chunk.setSnippet(limitSnippet(body, 260));
        chunk.setScore(baseScore + LegacyRetrievalPolicy.score(question, article.getTitle(), body));
        return chunk;
    }

    private KnowledgeChunk buildCommentChunk(TopCommentDTO comment, String question, Long articleId, int baseScore) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setUniqueKey("comment:" + comment.getCommentId());
        chunk.setSourceType("comment");
        chunk.setTitle(StringUtils.defaultIfBlank(comment.getUserName(), "匿名用户") + " 的评论");
        chunk.setArticleId(articleId);
        chunk.setCommentId(comment.getCommentId());
        chunk.setSnippet(limitSnippet(comment.getCommentContent(), 180));
        chunk.setScore(baseScore + LegacyRetrievalPolicy.score(question, chunk.getTitle(), comment.getCommentContent()));
        return chunk;
    }

    private String buildRagentPrompt(String question, List<KnowledgeChunk> chunks, List<AiAssistantHistoryItemDTO> history) {
        StringBuilder prompt = new StringBuilder(512);
        prompt.append("你是社区AI知识助手，请优先根据我提供的社区资料回答。")
                .append("如果资料不足，请明确说明不确定，不要编造。");

        if (history != null && !history.isEmpty()) {
            prompt.append("\n\n【最近会话】");
            for (AiAssistantHistoryItemDTO item : history.stream().limit(3).collect(Collectors.toList())) {
                prompt.append("\nQ: ").append(item.getQuestion());
                prompt.append("\nA: ").append(limitSnippet(item.getAnswer(), 120));
            }
        }

        if (chunks != null && !chunks.isEmpty()) {
            prompt.append("\n\n【社区资料】");
            int index = 1;
            for (KnowledgeChunk chunk : chunks) {
                prompt.append("\n[").append(index++).append("] ")
                        .append(chunk.getTitle())
                        .append("（").append(chunk.getSourceType()).append("）: ")
                        .append(chunk.getSnippet());
            }
        }
        prompt.append("\n\n【用户问题】\n").append(question);
        return prompt.toString();
    }

    private List<Map<String, String>> buildApiMessages(String question, List<KnowledgeChunk> chunks,
                                                       List<AiAssistantHistoryItemDTO> history, boolean deepThinking) {
        List<Map<String, String>> messages = new ArrayList<>();
        String systemPrompt = StringUtils.defaultIfBlank(properties.getApi().getSystemPrompt(),
                "你是社区 AI 知识助手，请优先根据给定资料回答，不要编造。");
        if (deepThinking) {
            systemPrompt = systemPrompt + " 请在回答前先整理资料中的关键线索，再输出最终答案。";
        }
        messages.add(buildMessage("system", systemPrompt));

        if (history != null) {
            for (AiAssistantHistoryItemDTO item : history.stream().limit(3).collect(Collectors.toList())) {
                messages.add(buildMessage("user", item.getQuestion()));
                messages.add(buildMessage("assistant", limitSnippet(item.getAnswer(), 300)));
            }
        }

        messages.add(buildMessage("user", buildRagentPrompt(question, chunks, Collections.emptyList())));
        return messages;
    }

    private Map<String, String> buildMessage(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildLocalFallbackAnswer(String question, List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "我暂时没有检索到和这个问题直接相关的社区资料。你可以换一种更具体的问法，或者带上文章标题、规则名、功能名再问我一次。";
        }

        StringBuilder answer = new StringBuilder(256);
        answer.append("我先基于社区现有资料给你一个可执行结论：\n");
        KnowledgeChunk first = chunks.get(0);
        answer.append("1. 最相关的线索来自《").append(first.getTitle()).append("》，核心内容是：")
                .append(first.getSnippet()).append("\n");
        if (chunks.size() > 1) {
            answer.append("2. 另外还命中了这些补充资料：\n");
            int index = 0;
            for (KnowledgeChunk chunk : chunks.stream().skip(1).limit(2).collect(Collectors.toList())) {
                answer.append("- ").append(chunk.getTitle()).append("：").append(chunk.getSnippet()).append("\n");
                index++;
            }
        }
        answer.append("3. 如果你是想解决“").append(question).append("”，建议优先按上面的规则/文章说明排查；")
                .append("如果你愿意，我也可以继续结合当前文章上下文帮你拆成更具体的步骤。");
        return answer.toString().trim();
    }

    private void saveHistory(String sessionId, Long userId, AiAssistantHistoryItemDTO item) {
        String key = historyRedisKey(userId, sessionId);
        stringRedisTemplate.opsForList().leftPush(key, JsonUtil.toStr(item));
        stringRedisTemplate.opsForList().trim(key, 0, properties.getMemoryLimit() - 1L);
        stringRedisTemplate.expire(key, properties.getMemoryTtlHours(), TimeUnit.HOURS);
    }

    private String historyRedisKey(Long userId, String sessionId) {
        return HISTORY_KEY_PREFIX + userId + ":" + sessionId;
    }

    private String generateSessionId(Long userId) {
        return userId + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String limitSearchKey(String question) {
        return LegacyRetrievalPolicy.normalizeSearchKey(question);
    }

    private String limitSnippet(String text, int max) {
        return LegacyRetrievalPolicy.limitSnippet(text, max);
    }

    private String trimTrailingSlash(String value) {
        return StringUtils.removeEnd(StringUtils.trimToEmpty(value), "/");
    }

    private String normalizeBearer(String token) {
        if (StringUtils.startsWithIgnoreCase(token, "Bearer ")) {
            return token;
        }
        return "Bearer " + token.trim();
    }

    @Data
    private static class KnowledgeChunk {
        private String uniqueKey;
        private String sourceType;
        private String title;
        private String snippet;
        private Long articleId;
        private Long commentId;
        private String configKey;
        private int score;

        public AiAssistantReferenceDTO toReference() {
            AiAssistantReferenceDTO dto = new AiAssistantReferenceDTO();
            dto.setSourceType(sourceType);
            dto.setTitle(title);
            dto.setSnippet(snippet);
            dto.setArticleId(articleId);
            dto.setCommentId(commentId);
            dto.setConfigKey(configKey);
            return dto;
        }
    }

    @Data
    private static class RagentMessageDelta {
        private String type;
        private String delta;
    }
}
