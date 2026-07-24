package com.github.paicoding.forum.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties({GatewayRouteProperties.class, GatewayAuthProperties.class})
public class GatewayConfig {

    @Bean
    public KeyResolver ipKeyResolver(GatewayRateLimitProperties properties) {
        return exchange -> {
            String key = null;
            if (properties.isTrustedProxy()) {
                key = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                if (StringUtils.isNotBlank(key)) {
                    key = StringUtils.substringBefore(key, ",").trim();
                }
            }
            if (StringUtils.isBlank(key) && exchange.getRequest().getRemoteAddress() != null) {
                key = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            }
            return Mono.just(StringUtils.defaultIfBlank(key, "unknown"));
        };
    }
}
