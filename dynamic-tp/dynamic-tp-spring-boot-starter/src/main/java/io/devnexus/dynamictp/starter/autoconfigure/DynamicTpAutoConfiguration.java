package io.devnexus.dynamictp.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devnexus.dynamictp.starter.alert.AlertNotifier;
import io.devnexus.dynamictp.starter.alert.DingTalkAlertNotifier;
import io.devnexus.dynamictp.starter.alert.LoggingAlertNotifier;
import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.core.DynamicThreadPoolCommandPublisher;
import io.devnexus.dynamictp.starter.core.DynamicThreadPoolConfigSynchronizer;
import io.devnexus.dynamictp.starter.core.DynamicThreadPoolManager;
import io.devnexus.dynamictp.starter.core.ThreadPoolInterceptor;
import io.devnexus.dynamictp.starter.core.ThreadPoolRegistry;
import io.devnexus.dynamictp.starter.metrics.DynamicThreadPoolMetrics;
import io.devnexus.dynamictp.starter.repository.NoopThreadPoolConfigRepository;
import io.devnexus.dynamictp.starter.repository.RedisThreadPoolConfigRepository;
import io.devnexus.dynamictp.starter.repository.ThreadPoolConfigRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DynamicTpProperties.class)
@ConditionalOnProperty(prefix = "dynamic.tp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicTpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolRegistry threadPoolRegistry() {
        return new ThreadPoolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public static ThreadPoolInterceptor threadPoolInterceptor(ThreadPoolRegistry threadPoolRegistry,
                                                              DynamicThreadPoolMetrics dynamicThreadPoolMetrics) {
        return new ThreadPoolInterceptor(threadPoolRegistry, dynamicThreadPoolMetrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean(name = "dynamicTpLoggingAlertNotifier")
    public AlertNotifier dynamicTpLoggingAlertNotifier() {
        return new LoggingAlertNotifier();
    }

    @Bean
    @ConditionalOnMissingBean(name = "dynamicTpDingTalkAlertNotifier")
    @ConditionalOnProperty(prefix = "dynamic.tp", name = "ding-talk-webhook")
    public AlertNotifier dynamicTpDingTalkAlertNotifier(DynamicTpProperties properties) {
        return new DingTalkAlertNotifier(properties.getDingTalkWebhook());
    }

    @Bean
    @ConditionalOnMissingBean(name = "dynamicThreadPoolMetrics")
    public DynamicThreadPoolMetrics dynamicThreadPoolMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider,
                                                             DynamicTpProperties properties) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            DynamicTpProperties fallback = new DynamicTpProperties();
            fallback.setMetricsEnabled(false);
            return new DynamicThreadPoolMetrics(new SimpleMeterRegistry(), fallback);
        }
        return new DynamicThreadPoolMetrics(meterRegistry, properties);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(name = "threadPoolConfigRepository")
    public ThreadPoolConfigRepository redisThreadPoolConfigRepository(StringRedisTemplate stringRedisTemplate,
                                                                      ObjectMapper objectMapper,
                                                                      DynamicTpProperties properties) {
        return new RedisThreadPoolConfigRepository(stringRedisTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "threadPoolConfigRepository")
    public ThreadPoolConfigRepository noopThreadPoolConfigRepository() {
        return new NoopThreadPoolConfigRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicThreadPoolManager dynamicThreadPoolManager(ThreadPoolRegistry threadPoolRegistry,
                                                             ObjectMapper objectMapper,
                                                             DynamicTpProperties properties,
                                                             java.util.List<AlertNotifier> alertNotifiers,
                                                             ThreadPoolConfigRepository threadPoolConfigRepository,
                                                             DynamicThreadPoolMetrics dynamicThreadPoolMetrics) {
        return new DynamicThreadPoolManager(threadPoolRegistry, objectMapper, properties, alertNotifiers,
                threadPoolConfigRepository, dynamicThreadPoolMetrics);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public DynamicThreadPoolCommandPublisher dynamicThreadPoolCommandPublisher(StringRedisTemplate stringRedisTemplate,
                                                                               ObjectMapper objectMapper,
                                                                               DynamicTpProperties properties,
                                                                               ThreadPoolConfigRepository threadPoolConfigRepository,
                                                                               DynamicThreadPoolMetrics dynamicThreadPoolMetrics) {
        return new DynamicThreadPoolCommandPublisher(stringRedisTemplate, objectMapper, properties,
                threadPoolConfigRepository, dynamicThreadPoolMetrics);
    }

    @Bean
    @ConditionalOnBean(ThreadPoolConfigRepository.class)
    @ConditionalOnMissingBean
    public DynamicThreadPoolConfigSynchronizer dynamicThreadPoolConfigSynchronizer(DynamicThreadPoolManager manager,
                                                                                   ThreadPoolConfigRepository threadPoolConfigRepository,
                                                                                   DynamicTpProperties properties,
                                                                                   DynamicThreadPoolMetrics dynamicThreadPoolMetrics) {
        return new DynamicThreadPoolConfigSynchronizer(manager, threadPoolConfigRepository, properties,
                dynamicThreadPoolMetrics);
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnClass(RedisMessageListenerContainer.class)
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                      DynamicThreadPoolManager manager,
                                                                      DynamicTpProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        if (StringUtils.hasText(properties.getRedisChannel())) {
            container.addMessageListener(manager, new PatternTopic(properties.getRedisChannel()));
        }
        return container;
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    @ConditionalOnMissingBean
    public DynamicThreadPoolEndpoint dynamicThreadPoolEndpoint(DynamicThreadPoolManager manager,
                                                               DynamicTpProperties properties,
                                                               ThreadPoolConfigRepository threadPoolConfigRepository) {
        return new DynamicThreadPoolEndpoint(manager, properties, threadPoolConfigRepository);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnMissingBean(name = "dynamicThreadPoolHealthIndicator")
    public HealthIndicator dynamicThreadPoolHealthIndicator(DynamicThreadPoolManager manager,
                                                            DynamicTpProperties properties) {
        return new DynamicThreadPoolHealthIndicator(manager, properties);
    }
}