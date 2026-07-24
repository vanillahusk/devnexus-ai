package com.github.paicoding.forum.service.notify.facade.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.PageListVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.service.discovery.LocalServiceDiscovery;
import com.github.paicoding.forum.service.notify.config.MessageServiceProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RemoteMessageFacadeSupport {

    private final MessageServiceProperties properties;
    private final LocalServiceDiscovery localServiceDiscovery;
    private final RestTemplate restTemplate;

    public RemoteMessageFacadeSupport(MessageServiceProperties properties,
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

    public int parseIntResult(JSONObject response) {
        Object result = response.get("result");
        return result == null ? 0 : Integer.parseInt(String.valueOf(result));
    }

    public PageListVo<com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO> parsePageListResult(JSONObject response) {
        Object result = response.get("result");
        if (result == null) {
            return PageListVo.emptyVo();
        }
        JSONObject root = JSON.parseObject(JSON.toJSONString(result));
        PageListVo<com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO> vo = new PageListVo<>();
        vo.setHasMore(root.getBoolean("hasMore"));
        JSONArray list = root.getJSONArray("list");
        vo.setList(list == null ? List.of() : JSON.parseArray(list.toJSONString(), com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO.class));
        return vo;
    }

    public Page<com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO> parsePageResult(JSONObject response) {
        JSONObject result = response.getJSONObject("result");
        Page<com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO> page = new Page<>();
        if (result == null) {
            return page;
        }
        JSONArray records = result.getJSONArray("records");
        page.setRecords(records == null ? List.of() : JSON.parseArray(records.toJSONString(), com.github.paicoding.forum.api.model.vo.notify.dto.NotifyMsgDTO.class));
        page.setCurrent(result.getLongValue("current"));
        page.setSize(result.getLongValue("size"));
        page.setTotal(result.getLongValue("total"));
        page.setPages(result.getLongValue("pages"));
        return page;
    }

    public Map<String, Integer> parseMapResult(JSONObject response) {
        Object result = response.get("result");
        if (result == null) {
            return Map.of();
        }
        JSONObject root = JSON.parseObject(JSON.toJSONString(result));
        Map<String, Integer> ans = new LinkedHashMap<>();
        root.forEach((k, v) -> ans.put(k, v == null ? 0 : Integer.parseInt(String.valueOf(v))));
        return ans;
    }

    private HttpHeaders buildHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.isBlank(properties.getToken())) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "消息服务调用 token 未配置");
        }
        headers.set(properties.getTokenHeader(), properties.getToken());
        if (userId != null) {
            headers.set(properties.getUserIdHeader(), String.valueOf(userId));
        }
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
        String msg = status == null ? "远端消息服务返回为空" : status.getString("msg");
        if (code != StatusEnum.SUCCESS.getCode()) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, StringUtils.defaultIfBlank(msg, "远端消息服务调用失败"));
        }
        return root;
    }
}
