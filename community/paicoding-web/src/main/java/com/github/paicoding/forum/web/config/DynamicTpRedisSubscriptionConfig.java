package com.github.paicoding.forum.web.config;

import com.interview.dynamictp.starter.config.DynamicTpProperties;
import com.interview.dynamictp.starter.core.DynamicThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class DynamicTpRedisSubscriptionConfig {

    @Bean(name = "dynamicTpRedisMessageListenerContainer")
    public RedisMessageListenerContainer dynamicTpRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            DynamicThreadPoolManager dynamicThreadPoolManager,
            DynamicTpProperties dynamicTpProperties
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        if (StringUtils.hasText(dynamicTpProperties.getRedisChannel())) {
            container.addMessageListener(dynamicThreadPoolManager, new PatternTopic(dynamicTpProperties.getRedisChannel()));
            log.info("[dynamic-tp] register redis refresh listener on channel: {}", dynamicTpProperties.getRedisChannel());
        } else {
            log.warn("[dynamic-tp] redis-channel is empty, skip redis refresh listener registration");
        }
        return container;
    }
}
