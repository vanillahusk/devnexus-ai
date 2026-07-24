package com.github.paicoding.forum.service.ai.facade.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.PageVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.discovery.LocalServiceDiscovery;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
public class RemoteAigcFacadeSupport {

    private final AiKnowledgeProperties properties;
    private final LocalServiceDiscovery localServiceDiscovery;
    private final RestTemplate restTemplate;

    public RemoteAigcFacadeSupport(AiKnowledgeProperties properties,
                                   LocalServiceDiscovery localServiceDiscovery) {
        this.properties = properties;
        this.localServiceDiscovery = localServiceDiscovery;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(20000);
        this.restTemplate = new RestTemplate(factory);
    }

    public JSONObject post(String path, Object body, Long userId) {
        HttpEntity<?> entity = new HttpEntity<>(body, buildHeaders(userId));
        ResponseEntity<String> response = restTemplate.exchange(buildUrl(path), HttpMethod.POST, entity, String.class);
        return parseAndValidate(response.getBody());
    }

    public JSONObject get(String path, Long userId) {
        HttpEntity<?> entity = new HttpEntity<>(buildHeaders(userId));
        ResponseEntity<String> response = restTemplate.exchange(buildUrl(path), HttpMethod.GET, entity, String.class);
        return parseAndValidate(response.getBody());
    }

    public <T> T parseObjectResult(JSONObject response, Class<T> targetClass) {
        Object result = response.get("result");
        if (result == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(result), targetClass);
    }

    public <T> List<T> parseListResult(JSONObject response, Class<T> targetClass) {
        Object result = response.get("result");
        if (result == null) {
            return Collections.emptyList();
        }
        return JSON.parseArray(JSON.toJSONString(result), targetClass);
    }

    public <T> PageVo<T> parsePageResult(JSONObject response, Class<T> targetClass) {
        JSONObject result = response.getJSONObject("result");
        if (result == null) {
            return PageVo.build(Collections.emptyList(), 10L, 1L, 0L);
        }
        JSONArray list = result.getJSONArray("list");
        List<T> rows = list == null ? Collections.emptyList() : JSON.parseArray(list.toJSONString(), targetClass);
        PageVo<T> page = new PageVo<>();
        page.setList(rows);
        page.setPageNum(result.getLongValue("pageNum"));
        page.setPageSize(result.getLongValue("pageSize"));
        page.setPageTotal(result.getLongValue("pageTotal"));
        page.setTotal(result.getLongValue("total"));
        return page;
    }

    private HttpHeaders buildHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.isBlank(properties.getService().getToken())) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "AI 远端服务调用 token 未配置");
        }
        headers.set(properties.getService().getTokenHeader(), properties.getService().getToken());
        if (userId != null) {
            headers.set(properties.getService().getUserIdHeader(), String.valueOf(userId));
        }
        return headers;
    }

    private String buildUrl(String path) {
        String base = StringUtils.removeEnd(
                localServiceDiscovery.resolveBaseUrl(properties.getService().getServiceId(), properties.getService().getBaseUrl()), "/");
        String suffix = StringUtils.startsWith(path, "/") ? path : "/" + path;
        return base + suffix;
    }

    private JSONObject parseAndValidate(String raw) {
        JSONObject root = StringUtils.isBlank(raw) ? new JSONObject() : JSONObject.parseObject(raw);
        JSONObject status = root.getJSONObject("status");
        int code = status == null ? StatusEnum.UNEXPECT_ERROR.getCode() : status.getIntValue("code");
        String msg = status == null ? "远端 AIGC 服务返回为空" : status.getString("msg");
        if (code != StatusEnum.SUCCESS.getCode()) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, StringUtils.defaultIfBlank(msg, "远端 AIGC 服务调用失败"));
        }
        return root;
    }
}
