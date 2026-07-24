package com.github.paicoding.forum.service.feign.auth;

import com.github.paicoding.forum.service.user.config.AuthServiceProperties;
import feign.RequestInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;

public class AuthFeignConfiguration {

    @Bean
    public RequestInterceptor authInternalTokenInterceptor(AuthServiceProperties properties) {
        return template -> {
            if (StringUtils.isNotBlank(properties.getToken())) {
                template.header(properties.getTokenHeader(), properties.getToken());
            }
        };
    }
}
