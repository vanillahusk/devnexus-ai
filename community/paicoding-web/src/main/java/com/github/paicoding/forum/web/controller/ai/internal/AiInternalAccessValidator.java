package com.github.paicoding.forum.web.controller.ai.internal;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiInternalAccessValidator {

    private final AiKnowledgeProperties properties;

    public void validate(String token) {
        String configured = properties.getService().getToken();
        if (StringUtils.isBlank(configured)) {
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "AI internal token 未配置");
        }
        if (!StringUtils.equals(configured, token)) {
            throw ExceptionUtil.of(StatusEnum.FORBID_ERROR_MIXED, "非法的 AI internal token");
        }
    }
}
