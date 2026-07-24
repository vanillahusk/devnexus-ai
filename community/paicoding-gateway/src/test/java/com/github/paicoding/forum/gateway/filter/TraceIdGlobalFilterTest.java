package com.github.paicoding.forum.gateway.filter;

import com.github.paicoding.forum.gateway.config.GatewayTraceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdGlobalFilterTest {

    private final GatewayTraceProperties properties = new GatewayTraceProperties();
    private final TraceIdGlobalFilter filter = new TraceIdGlobalFilter(properties);

    @Test
    void shouldKeepSafeTraceIdAndPropagateIt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/articles").header("X-Trace-Id", "trace-id-12345678"));
        AtomicReference<String> downstreamTraceId = new AtomicReference<>();
        GatewayFilterChain chain = filtered -> {
            downstreamTraceId.set(filtered.getRequest().getHeaders().getFirst("X-Trace-Id"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(downstreamTraceId.get()).isEqualTo("trace-id-12345678");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo("trace-id-12345678");
    }

    @Test
    void shouldReplaceUnsafeTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/articles").header("X-Trace-Id", "bad trace id"));
        AtomicReference<String> downstreamTraceId = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            downstreamTraceId.set(filtered.getRequest().getHeaders().getFirst("X-Trace-Id"));
            return Mono.empty();
        }).block();

        assertThat(downstreamTraceId.get()).matches("[a-f0-9]{32}");
    }
}
