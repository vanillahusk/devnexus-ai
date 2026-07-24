package com.github.paicoding.forum.service.user.facade.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.service.discovery.LocalServiceDiscovery;
import com.github.paicoding.forum.service.user.config.AuthServiceProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class RemoteAuthFacadeSupport {

    private final AuthServiceProperties properties;
    private final LocalServiceDiscovery localServiceDiscovery;
    private final RestTemplate restTemplate;

    public RemoteAuthFacadeSupport(AuthServiceProperties properties,
                                   LocalServiceDiscovery localServiceDiscovery) {
        this.properties = properties;
        this.localServiceDiscovery = localServiceDiscovery;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(20000);
        this.restTemplate = new RestTemplate(factory);
    }

    public JSONObject post(String path, Object body) {
        HttpEntity<?> entity = new HttpEntity<>(body, buildHeaders());
        ResponseEntity<String> response = restTemplate.exchange(buildUrl(path), HttpMethod.POST, entity, String.class);
        return parseAndValidate(response.getBody());
    }

    public JSONObject get(String path) {
        HttpEntity<?> entity = new HttpEntity<>(buildHeaders());
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

    public String parseStringResult(JSONObject response) {
        Object result = response.get("result");
        return result == null ? null : String.valueOf(result);
    }

    public Long parseLongResult(JSONObject response) {
        Object result = response.get("result");
        return result == null ? null : Long.valueOf(String.valueOf(result));
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.isBlank(properties.getToken())) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "认证服务调用 token 未配置");
        }
        headers.set(properties.getTokenHeader(), properties.getToken());
        return headers;
    }

    private String buildUrl(String path) {
        String base = StringUtils.removeEnd(
                localServiceDiscovery.resolveBaseUrl(properties.getServiceId(), properties.getBaseUrl()), "/");
        String suffix = StringUtils.startsWith(path, "/") ? path : "/" + path;
        return base + suffix;
    }

    private JSONObject parseAndValidate(String raw) {
        JSONObject root = StringUtils.isBlank(raw) ? new JSONObject() : JSONObject.parseObject(raw);
        JSONObject status = root.getJSONObject("status");
        int code = status == null ? StatusEnum.UNEXPECT_ERROR.getCode() : status.getIntValue("code");
        String msg = status == null ? "远端认证服务返回为空" : status.getString("msg");
        if (code != StatusEnum.SUCCESS.getCode()) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, StringUtils.defaultIfBlank(msg, "远端认证服务调用失败"));
        }
        return root;
    }
}
