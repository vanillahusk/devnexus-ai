package com.github.paicoding.forum.service.ai.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.ai.AiAgentAskReq;
import com.github.paicoding.forum.api.model.vo.ai.AiAgentReplyDTO;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.core.mdc.MdcUtil;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiAgentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 使用服务端凭据调用 Ragent。浏览器不会接触 Ragent Token，响应只映射公开 DTO。
 */
@Slf4j
@Service
public class RagentAiAgentServiceImpl implements AiAgentService {
    private static final String AUTH_TOKEN_CACHE_KEY = "ai:knowledge:ragent:auth-token";
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private final StringRedisTemplate redisTemplate;
    private final AiKnowledgeProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public RagentAiAgentServiceImpl(StringRedisTemplate redisTemplate,
                                    AiKnowledgeProperties properties) {
        this(redisTemplate, properties, buildRestTemplate(properties));
    }

    RagentAiAgentServiceImpl(StringRedisTemplate redisTemplate,
                             AiKnowledgeProperties properties,
                             RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    @Trace(operationName = "rag.agent.community_query")
    public AiAgentReplyDTO query(AiAgentAskReq request, Long userId) {
        validate(request, userId);
        if (!properties.getRagent().isEnabled()) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "Ragent 未启用");
        }

        JSONObject response = exchange(properties.getRagent().getAgentPath(), HttpMethod.POST,
                Map.of("question", request.getQuestion().strip(), "sessionId", request.getSessionId()),
                authenticatedHeaders());
        if (!isSuccess(response)) {
            String message = StringUtils.defaultIfBlank(response.getString("message"), "受控 Agent 调用失败");
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, message);
        }

        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "受控 Agent 未返回结果");
        }
        AiAgentReplyDTO reply = data.toJavaObject(AiAgentReplyDTO.class);
        reply.setTraceId(StringUtils.defaultIfBlank(MdcUtil.getTraceId(), response.getString("requestId")));
        return reply;
    }

    private void validate(AiAgentAskReq request, Long userId) {
        if (userId == null) {
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "请先登录");
        }
        if (request == null || StringUtils.isBlank(request.getQuestion())
                || request.getQuestion().length() > 500) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "问题长度必须在1到500之间");
        }
        if (StringUtils.isBlank(request.getSessionId())
                || !SAFE_SESSION_ID.matcher(request.getSessionId()).matches()) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "sessionId格式非法");
        }
    }

    private HttpHeaders authenticatedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String traceId = MdcUtil.getTraceId();
        if (StringUtils.isNotBlank(traceId)) {
            headers.set("X-Trace-Id", traceId);
        }
        String token = resolveToken();
        if (StringUtils.isBlank(token)) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "Ragent 服务端凭据未配置");
        }
        headers.set("Authorization", normalizeToken(token));
        return headers;
    }

    private String resolveToken() {
        AiKnowledgeProperties.Ragent ragent = properties.getRagent();
        if (StringUtils.isNotBlank(ragent.getToken())) {
            return ragent.getToken();
        }
        String cached = redisTemplate.opsForValue().get(AUTH_TOKEN_CACHE_KEY);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }
        if (StringUtils.isAnyBlank(ragent.getUsername(), ragent.getPassword())) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JSONObject response = exchange("/auth/login", HttpMethod.POST,
                Map.of("username", ragent.getUsername(), "password", ragent.getPassword()), headers);
        if (!isSuccess(response) || response.getJSONObject("data") == null) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "登录 Ragent 失败");
        }
        String token = normalizeToken(response.getJSONObject("data").getString("token"));
        if (StringUtils.isNotBlank(token)) {
            redisTemplate.opsForValue().set(AUTH_TOKEN_CACHE_KEY, token, 12, TimeUnit.HOURS);
        }
        return token;
    }

    private JSONObject exchange(String path, HttpMethod method, Object body, HttpHeaders headers) {
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(buildUrl(path), method, entity, String.class);
        return StringUtils.isBlank(response.getBody())
                ? new JSONObject()
                : JSONObject.parseObject(response.getBody());
    }

    private String buildUrl(String path) {
        return StringUtils.removeEnd(properties.getRagent().getBaseUrl(), "/") + path;
    }

    private boolean isSuccess(JSONObject response) {
        return response != null && StringUtils.equals("0", response.getString("code"));
    }

    private String normalizeToken(String token) {
        String normalized = StringUtils.trimToEmpty(token);
        if (StringUtils.startsWithIgnoreCase(normalized, "Bearer ")) {
            return StringUtils.trim(normalized.substring("Bearer ".length()));
        }
        return normalized;
    }

    private static RestTemplate buildRestTemplate(AiKnowledgeProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getRagent().getConnectTimeoutMs());
        factory.setReadTimeout(properties.getRagent().getAgentReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
