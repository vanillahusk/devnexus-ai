package com.github.paicoding.forum.message.controller.internal;

import com.github.paicoding.forum.service.notify.config.MessageServiceProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
@RequiredArgsConstructor
public class MessageInternalAccessValidator {

    private final MessageServiceProperties properties;

    public void validate(String token) {
        String configured = properties.getToken();
        if (StringUtils.isBlank(configured)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "消息服务 internal token 未配置");
        }
        if (!StringUtils.equals(configured, token)) {
            throw new ResponseStatusException(FORBIDDEN, "非法的消息服务 internal token");
        }
    }
}
