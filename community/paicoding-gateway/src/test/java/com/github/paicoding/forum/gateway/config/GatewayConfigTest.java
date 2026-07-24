package com.github.paicoding.forum.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayConfigTest {

    @Test
    void shouldIgnoreSpoofedForwardedForByDefault() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        KeyResolver resolver = new GatewayConfig().ipKeyResolver(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-For", "203.0.113.10"));

        assertThat(resolver.resolve(exchange).block()).isEqualTo("127.0.0.1");
    }
}
