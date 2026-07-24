package com.github.paicoding.forum.service.feign.aigc;

import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import feign.RequestInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;

public class AigcFeignConfiguration {

    @Bean
    public RequestInterceptor aigcInternalTokenInterceptor(AiKnowledgeProperties properties) {
        return template -> {
            AiKnowledgeProperties.Service service = properties.getService();
            if (StringUtils.isNotBlank(service.getToken())) {
                template.header(service.getTokenHeader(), service.getToken());
            }
        };
    }
}
