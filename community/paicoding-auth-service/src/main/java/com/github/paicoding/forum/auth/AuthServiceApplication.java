package com.github.paicoding.forum.auth;

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
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableAsync
@EnableScheduling
@EnableCaching
@MapperScan(basePackages = {
        "com.github.paicoding.forum.service.article.repository.mapper",
        "com.github.paicoding.forum.service.user.repository.mapper",
        "com.github.paicoding.forum.service.comment.repository.mapper",
        "com.github.paicoding.forum.service.config.repository.mapper",
        "com.github.paicoding.forum.service.statistics.repository.mapper",
        "com.github.paicoding.forum.service.notify.repository.mapper"
})
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.github.paicoding.forum.core",
        "com.github.paicoding.forum.service.user.repository",
        "com.github.paicoding.forum.service.user.converter",
        "com.github.paicoding.forum.service.user.service.user",
        "com.github.paicoding.forum.service.user.service.ai",
        "com.github.paicoding.forum.service.user.service.help",
        "com.github.paicoding.forum.service.user.service.conf",
        "com.github.paicoding.forum.service.user.config",
        "com.github.paicoding.forum.auth"
}, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.github.paicoding.forum.service.user.service.user.UserServiceImpl.class
))
public class AuthServiceApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        JacksonTypeHandler.setObjectMapper(new ObjectMapper());
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedHeaders("*")
                .allowedMethods("POST", "GET", "PUT", "OPTIONS", "DELETE")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
