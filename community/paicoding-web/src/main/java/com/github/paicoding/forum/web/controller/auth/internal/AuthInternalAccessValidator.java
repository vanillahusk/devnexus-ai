package com.github.paicoding.forum.web.controller.auth.internal;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.service.user.config.AuthServiceProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthInternalAccessValidator {

    private final AuthServiceProperties properties;

    public void validate(String token) {
        String configured = properties.getToken();
        if (StringUtils.isBlank(configured)) {
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "认证服务 internal token 未配置");
        }
        if (!StringUtils.equals(configured, token)) {
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "非法的认证服务 internal token");
        }
    }
}
