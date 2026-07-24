package com.github.paicoding.forum.service.feign.message;

import com.github.paicoding.forum.service.notify.config.MessageServiceProperties;
import feign.RequestInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;

public class MessageFeignConfiguration {

    @Bean
    public RequestInterceptor messageInternalTokenInterceptor(MessageServiceProperties properties) {
        return template -> {
            if (StringUtils.isNotBlank(properties.getToken())) {
                template.header(properties.getTokenHeader(), properties.getToken());
            }
        };
    }
}
