package com.github.paicoding.forum.aigc;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 独立 AIGC 服务启动入口
 */
@EnableAsync
@EnableScheduling
@EnableCaching
@MapperScan(basePackages = {
        "com.github.paicoding.forum.service.article.repository.mapper",
        "com.github.paicoding.forum.service.user.repository.mapper",
        "com.github.paicoding.forum.service.comment.repository.mapper",
        "com.github.paicoding.forum.service.config.repository.mapper",
        "com.github.paicoding.forum.service.statistics.repository.mapper",
        "com.github.paicoding.forum.service.notify.repository.mapper",
        "com.github.paicoding.forum.service.ai.repository.mapper"
})
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.github.paicoding.forum.core",
        "com.github.paicoding.forum.service",
        "com.github.paicoding.forum.aigc"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.github\\.paicoding\\.forum\\.service\\.user\\.service\\.favor\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.github\\.paicoding\\.forum\\.service\\.test\\..*")
})
public class AigcServiceApplication {

    public static void main(String[] args) {
        JacksonTypeHandler.setObjectMapper(new ObjectMapper());
        SpringApplication.run(AigcServiceApplication.class, args);
    }
}
