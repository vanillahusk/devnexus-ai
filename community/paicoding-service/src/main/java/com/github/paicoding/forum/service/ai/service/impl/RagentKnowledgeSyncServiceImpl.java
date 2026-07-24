package com.github.paicoding.forum.service.ai.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.ai.dto.AiKnowledgeDocDTO;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.core.mdc.MdcUtil;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.RagentKnowledgeSyncService;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * ragent 知识库同步实现
 *
 * @author Codex
 * @date 2026-04-01
 */
@Slf4j
@Service
public class RagentKnowledgeSyncServiceImpl implements RagentKnowledgeSyncService {
    private static final String KB_ID_CACHE_KEY = "ai:knowledge:ragent:kb-id";
    private static final String DOC_MAPPING_KEY = "ai:knowledge:ragent:doc-mapping";
    private static final String DOC_SYNC_STATUS_KEY = "ai:knowledge:ragent:sync-status";
    /** Ragent 当前复用 collectionName 作为 S3 bucket，先采用二者都安全的保守命名子集。 */
    private static final Pattern SHARED_STORAGE_NAME_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$");

    private final StringRedisTemplate stringRedisTemplate;
    private final AiKnowledgeProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public RagentKnowledgeSyncServiceImpl(StringRedisTemplate stringRedisTemplate,
                                          AiKnowledgeProperties properties) {
        this(stringRedisTemplate, properties, buildRestTemplate(properties));
    }

    public RagentKnowledgeSyncServiceImpl(StringRedisTemplate stringRedisTemplate,
                                          AiKnowledgeProperties properties,
                                          RestTemplate restTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public void autoSync(AiKnowledgeDocDTO doc) {
        if (!properties.getRagent().isEnabled() || !properties.getRagent().isAutoSync()) {
            return;
        }
        try {
            sync(doc);
            markStatus(doc.getKey(), "SYNCED");
        } catch (Exception e) {
            log.warn("知识文档自动同步到 ragent 失败, key:{}, msg:{}", doc.getKey(), e.getMessage());
            markStatus(doc.getKey(), "FAILED:" + e.getMessage());
        }
    }

    @Override
    @Trace(operationName = "rag.index.sync_to_ragent")
    public void sync(AiKnowledgeDocDTO doc) {
        syncInternal(doc, properties.getRagent().getCollectionName(), properties.getRagent().getKbName(),
                KB_ID_CACHE_KEY, DOC_MAPPING_KEY, DOC_SYNC_STATUS_KEY);
    }

    @Override
    public void syncToGeneration(AiKnowledgeDocDTO doc, String physicalCollection) {
        String suffix = requireStorageName(physicalCollection);
        syncInternal(doc, suffix, properties.getRagent().getKbName() + "-" + suffix,
                "ai:knowledge:ragent:generation:kb-id:" + suffix,
                "ai:knowledge:ragent:generation:doc-mapping:" + suffix,
                "ai:knowledge:ragent:generation:sync-status:" + suffix);
    }

    @Override
    public void deleteFromGeneration(String configKey, String physicalCollection) {
        String suffix = requireStorageName(physicalCollection);
        String mappingKey = "ai:knowledge:ragent:generation:doc-mapping:" + suffix;
        String statusKey = "ai:knowledge:ragent:generation:sync-status:" + suffix;
        Object docIdValue = stringRedisTemplate.opsForHash().get(mappingKey, configKey);
        if (!(docIdValue instanceof String docId) || StringUtils.isBlank(docId)) return;
        deleteRemoteDocStrict(docId);
        stringRedisTemplate.opsForHash().delete(mappingKey, configKey);
        stringRedisTemplate.opsForHash().delete(statusKey, configKey);
    }

    private void syncInternal(AiKnowledgeDocDTO doc, String collectionName, String kbName,
                              String kbCacheKey, String docMappingKey, String syncStatusKey) {
        if (!properties.getRagent().isEnabled()) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "ragent 同步未开启");
        }
        if (doc == null || StringUtils.isAnyBlank(doc.getKey(), doc.getTitle(), doc.getExportMarkdown())) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "知识文档缺少同步必要字段");
        }

        String kbId = ensureKnowledgeBase(collectionName, kbName, kbCacheKey);
        String oldDocId = stringRedisTemplate.opsForHash().get(docMappingKey, doc.getKey()) instanceof String str ? str : null;
        String newDocId = uploadDoc(kbId, doc);
        try {
            startChunk(newDocId);
            awaitChunkSuccess(newDocId);
            stringRedisTemplate.opsForHash().put(docMappingKey, doc.getKey(), newDocId);
            if (StringUtils.isNotBlank(oldDocId) && !StringUtils.equals(oldDocId, newDocId)) {
                try {
                    deleteRemoteDocStrict(oldDocId);
                } catch (RuntimeException deleteFailure) {
                    stringRedisTemplate.opsForHash().put(docMappingKey, doc.getKey(), oldDocId);
                    throw deleteFailure;
                }
            }
            markStatusQuietly(syncStatusKey, doc.getKey(), "SYNCED");
        } catch (RuntimeException failure) {
            deleteRemoteDocQuietly(newDocId);
            throw failure;
        }
    }

    @Override
    public void deleteByConfigKey(String configKey) {
        if (!properties.getRagent().isEnabled() || StringUtils.isBlank(configKey)) {
            return;
        }
        Object docIdObj = stringRedisTemplate.opsForHash().get(DOC_MAPPING_KEY, configKey);
        if (!(docIdObj instanceof String docId) || StringUtils.isBlank(docId)) {
            return;
        }
        deleteRemoteDocQuietly(docId);
        stringRedisTemplate.opsForHash().delete(DOC_MAPPING_KEY, configKey);
        stringRedisTemplate.opsForHash().delete(DOC_SYNC_STATUS_KEY, configKey);
    }

    @Override
    public void deleteStrictByConfigKey(String configKey) {
        if (!properties.getRagent().isEnabled()) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "ragent 同步未开启");
        }
        if (StringUtils.isBlank(configKey)) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "知识文档映射键不能为空");
        }
        Object docIdObj = stringRedisTemplate.opsForHash().get(DOC_MAPPING_KEY, configKey);
        if (!(docIdObj instanceof String docId) || StringUtils.isBlank(docId)) {
            return;
        }
        JSONObject response = exchangeJson("/knowledge-base/docs/" + docId, HttpMethod.DELETE,
                null, defaultJsonHeaders());
        assertSuccess(response, "删除 ragent 文档失败");
        stringRedisTemplate.opsForHash().delete(DOC_MAPPING_KEY, configKey);
        stringRedisTemplate.opsForHash().delete(DOC_SYNC_STATUS_KEY, configKey);
    }

    @Override
    public void syncAll(List<AiKnowledgeDocDTO> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        for (AiKnowledgeDocDTO doc : docs) {
            sync(doc);
        }
    }

    @Override
    public GenerationState beginGeneration(String generationLabel, long startWatermark) {
        return generationCommand("/admin/rag/index-generations/begin", Map.of(
                "collectionName", properties.getRagent().getCollectionName(),
                "generationLabel", generationLabel,
                "startWatermark", startWatermark));
    }

    @Override
    public GenerationState recordGenerationProgress(String generationLabel, long appliedWatermark,
                                                    long targetWatermark, boolean reconciled) {
        return generationCommand("/admin/rag/index-generations/progress", Map.of(
                "collectionName", properties.getRagent().getCollectionName(),
                "generationLabel", generationLabel,
                "appliedWatermark", appliedWatermark,
                "targetWatermark", targetWatermark,
                "reconciled", reconciled));
    }

    @Override
    public GenerationState activateGeneration(String generationLabel) {
        return generationCommand("/admin/rag/index-generations/activate", Map.of(
                "collectionName", properties.getRagent().getCollectionName(),
                "generationLabel", generationLabel));
    }

    @Override
    public GenerationState failGeneration(String generationLabel) {
        return generationCommand("/admin/rag/index-generations/fail", Map.of(
                "collectionName", properties.getRagent().getCollectionName(),
                "generationLabel", generationLabel));
    }

    @Override
    public Map<Long, ArticleVersionSummary> generationArticleVersions(String physicalCollection) {
        requireStorageName(physicalCollection);
        JSONObject response = exchangeJson("/admin/rag/index-generations/article-versions?collectionName="
                        + urlEncode(physicalCollection), HttpMethod.GET, null, defaultJsonHeaders());
        assertSuccess(response, "查询 ragent Generation 文章版本失败");
        JSONObject data = response.getJSONObject("data");
        Map<Long, ArticleVersionSummary> result = new LinkedHashMap<>();
        if (data == null) return result;
        for (String articleId : data.keySet()) {
            JSONObject summary = data.getJSONObject(articleId);
            if (summary == null) continue;
            result.put(Long.valueOf(articleId), new ArticleVersionSummary(
                    summary.getLongValue("minVersion"), summary.getLongValue("maxVersion"),
                    summary.getLongValue("chunkCount")));
        }
        return result;
    }

    private GenerationState generationCommand(String path, Map<String, Object> body) {
        JSONObject response = exchangeJson(path, HttpMethod.POST, body, defaultJsonHeaders());
        assertSuccess(response, "ragent Generation 操作失败");
        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "ragent Generation 未返回状态");
        }
        return new GenerationState(data.getString("logicalCollection"), data.getString("activeGeneration"),
                data.getString("buildingGeneration"), data.getString("previousGeneration"),
                data.getString("status"), data.getLongValue("startWatermark"),
                data.getLongValue("appliedWatermark"), data.getLongValue("targetWatermark"),
                data.getBooleanValue("reconciled"));
    }

    private String ensureKnowledgeBase(String collectionName, String kbName, String cacheKey) {
        requireStorageName(collectionName);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }

        String found = queryKnowledgeBaseIdByName(kbName);
        if (StringUtils.isNotBlank(found)) {
            cacheKbId(cacheKey, found);
            return found;
        }

        HttpHeaders headers = defaultJsonHeaders();
        Map<String, Object> body = Map.of(
                "name", kbName,
                "embeddingModel", properties.getRagent().getEmbeddingModel(),
                "collectionName", collectionName
        );
        JSONObject response = exchangeJson("/knowledge-base", HttpMethod.POST, body, headers);
        String kbId = parseDataString(response);
        if (StringUtils.isBlank(kbId)) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "创建 ragent 知识库失败");
        }
        cacheKbId(cacheKey, kbId);
        return kbId;
    }

    private String queryKnowledgeBaseIdByName(String kbName) {
        HttpHeaders headers = defaultJsonHeaders();
        String path = "/knowledge-base?current=1&size=20&name=" + urlEncode(kbName);
        JSONObject response = exchangeJson(path, HttpMethod.GET, null, headers);
        if (!isSuccess(response)) {
            return null;
        }
        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            return null;
        }
        JSONArray records = data.getJSONArray("records");
        if (records == null || records.isEmpty()) {
            return null;
        }
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (record != null && StringUtils.equals(record.getString("name"), kbName)) {
                return record.getString("id");
            }
        }
        return null;
    }

    private String uploadDoc(String kbId, AiKnowledgeDocDTO doc) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(doc.getExportMarkdown().getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return buildFilename(doc);
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", resource);
        form.add("sourceType", "file");
        form.add("processMode", "chunk");
        form.add("chunkStrategy", "structure_aware");
        form.add("chunkConfig", "{\"targetChars\":1200,\"maxChars\":1800,\"minChars\":400,\"overlapChars\":80,"
                + "\"targetTokens\":650,\"maxTokens\":800,\"minTokens\":500,\"overlapTokens\":75}");

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(form, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                buildUrl("/knowledge-base/" + kbId + "/docs/upload"), HttpMethod.POST, entity, String.class);
        JSONObject json = parseResponse(response.getBody());
        assertSuccess(json, "上传知识文档到 ragent 失败");
        JSONObject data = json.getJSONObject("data");
        String docId = data == null ? null : data.getString("id");
        if (StringUtils.isBlank(docId)) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "ragent 未返回文档ID");
        }
        return docId;
    }

    private void startChunk(String docId) {
        JSONObject response = exchangeJson("/knowledge-base/docs/" + docId + "/chunk", HttpMethod.POST, null, defaultJsonHeaders());
        assertSuccess(response, "触发 ragent 文档分块失败");
    }

    private void awaitChunkSuccess(String docId) {
        long timeoutMs = Math.max(1000, properties.getRagent().getChunkWaitTimeoutMs());
        long pollIntervalMs = Math.max(100, properties.getRagent().getChunkPollIntervalMs());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            JSONObject response = exchangeJson("/knowledge-base/docs/" + docId,
                    HttpMethod.GET, null, defaultJsonHeaders());
            assertSuccess(response, "查询 ragent 文档状态失败");
            JSONObject data = response.getJSONObject("data");
            String status = data == null ? null : data.getString("status");
            if (StringUtils.equalsIgnoreCase(status, "success")) {
                Integer chunkCount = data.getInteger("chunkCount");
                if (chunkCount == null || chunkCount <= 0) {
                    throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR,
                            "ragent 文档成功但没有可检索 Chunk, docId=" + docId);
                }
                return;
            }
            if (StringUtils.equalsIgnoreCase(status, "failed")) {
                throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR,
                        "ragent 文档分块失败, docId=" + docId);
            }
            sleepBeforePoll(pollIntervalMs);
        }
        throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR,
                "等待 ragent 文档可检索超时, docId=" + docId + ", timeoutMs=" + timeoutMs);
    }

    private void sleepBeforePoll(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "等待 ragent 文档状态时线程被中断");
        }
    }

    private void deleteRemoteDocStrict(String docId) {
        JSONObject response = exchangeJson("/knowledge-base/docs/" + docId,
                HttpMethod.DELETE, null, defaultJsonHeaders());
        assertSuccess(response, "删除旧 ragent 文档失败");
    }

    private void deleteRemoteDocQuietly(String docId) {
        try {
            JSONObject response = exchangeJson("/knowledge-base/docs/" + docId, HttpMethod.DELETE, null, defaultJsonHeaders());
            if (!isSuccess(response)) {
                log.warn("删除 ragent 文档失败, docId:{}, response:{}", docId, response);
            }
        } catch (Exception e) {
            log.warn("删除 ragent 文档异常, docId:{}, msg:{}", docId, e.getMessage());
        }
    }

    private JSONObject exchangeJson(String path, HttpMethod method, Object body, HttpHeaders headers) {
        HttpEntity<?> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(buildUrl(path), method, entity, String.class);
        return parseResponse(response.getBody());
    }

    private JSONObject parseResponse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return new JSONObject();
        }
        return JSONObject.parseObject(raw);
    }

    private HttpHeaders defaultJsonHeaders() {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String traceId = MdcUtil.getTraceId();
        if (StringUtils.isNotBlank(traceId)) {
            headers.set("X-Trace-Id", traceId);
        }
        String token = resolveToken();
        if (StringUtils.isNotBlank(token)) {
            headers.set("Authorization", token);
        }
        return headers;
    }

    private String resolveToken() {
        if (StringUtils.isNotBlank(properties.getRagent().getToken())) {
            return normalizeRagentToken(properties.getRagent().getToken());
        }
        String cacheKey = "ai:knowledge:ragent:auth-token";
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cached)) {
            return normalizeRagentToken(cached);
        }
        if (StringUtils.isAnyBlank(properties.getRagent().getUsername(), properties.getRagent().getPassword())) {
            return null;
        }
        JSONObject response = exchangeJson("/auth/login", HttpMethod.POST,
                Map.of("username", properties.getRagent().getUsername(), "password", properties.getRagent().getPassword()),
                new HttpHeaders() {{
                    setContentType(MediaType.APPLICATION_JSON);
                }});
        assertSuccess(response, "登录 ragent 失败");
        JSONObject data = response.getJSONObject("data");
        String token = data == null ? null : data.getString("token");
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String normalizedToken = normalizeRagentToken(token);
        stringRedisTemplate.opsForValue().set(cacheKey, normalizedToken, 12, TimeUnit.HOURS);
        return normalizedToken;
    }

    private boolean isSuccess(JSONObject json) {
        return json != null && StringUtils.equals("0", json.getString("code"));
    }

    private void assertSuccess(JSONObject json, String message) {
        if (!isSuccess(json)) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, message + ":" + (json == null ? "empty" : json.getString("message")));
        }
    }

    private String parseDataString(JSONObject json) {
        return json == null ? null : json.getString("data");
    }

    private void cacheKbId(String cacheKey, String kbId) {
        stringRedisTemplate.opsForValue().set(cacheKey, kbId, 7, TimeUnit.DAYS);
    }

    private void markStatus(String configKey, String status) {
        stringRedisTemplate.opsForHash().put(DOC_SYNC_STATUS_KEY, configKey, status);
    }

    private void markStatusQuietly(String configKey, String status) {
        markStatusQuietly(DOC_SYNC_STATUS_KEY, configKey, status);
    }

    private void markStatusQuietly(String statusKey, String configKey, String status) {
        try {
            stringRedisTemplate.opsForHash().put(statusKey, configKey, status);
        } catch (RuntimeException failure) {
            log.warn("记录 ragent 文档同步状态失败, key:{}, status:{}, msg:{}",
                    configKey, status, failure.getMessage());
        }
    }

    private String requireStorageName(String collectionName) {
        if (StringUtils.isBlank(collectionName) || !SHARED_STORAGE_NAME_PATTERN.matcher(collectionName).matches()) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED,
                    "ragent collectionName 必须为 3-63 位小写字母、数字或连字符，且首尾只能是字母或数字");
        }
        return collectionName;
    }

    private String normalizeRagentToken(String token) {
        String normalized = StringUtils.trimToEmpty(token);
        if (StringUtils.startsWithIgnoreCase(normalized, "Bearer ")) {
            return StringUtils.trim(normalized.substring("Bearer ".length()));
        }
        return normalized;
    }

    private String buildUrl(String path) {
        return StringUtils.removeEnd(properties.getRagent().getBaseUrl(), "/") + path;
    }

    private String buildFilename(AiKnowledgeDocDTO doc) {
        String base = StringUtils.defaultIfBlank(doc.getCode(), "knowledge-doc");
        return base.replaceAll("[^a-zA-Z0-9_-]+", "-") + ".md";
    }

    private String urlEncode(String value) {
        return value == null ? "" : value.replace(" ", "%20");
    }

    private static RestTemplate buildRestTemplate(AiKnowledgeProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, properties.getRagent().getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(1, properties.getRagent().getReadTimeoutMs()));
        return new RestTemplate(factory);
    }
}
