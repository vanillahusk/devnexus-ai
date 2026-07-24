package com.github.paicoding.forum.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author XuYifei
 * @date 2024-07-12
 */
@Configuration
@ConditionalOnProperty(name = "paicoding.service.auto-config.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan("com.github.paicoding.forum.service")
@MapperScan(basePackages = {
        "com.github.paicoding.forum.service.article.repository.mapper",
        "com.github.paicoding.forum.service.user.repository.mapper",
        "com.github.paicoding.forum.service.comment.repository.mapper",
        "com.github.paicoding.forum.service.config.repository.mapper",
        "com.github.paicoding.forum.service.statistics.repository.mapper",
        "com.github.paicoding.forum.service.notify.repository.mapper",
        "com.github.paicoding.forum.service.ai.repository.mapper",})
public class ServiceAutoConfig {

}
