package com.github.paicoding.forum.gateway.filter;

import com.github.paicoding.forum.gateway.config.GatewayTraceProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private final GatewayTraceProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String headerName = properties.getHeaderName();
        String traceId = exchange.getRequest().getHeaders().getFirst(headerName);
        if (StringUtils.isBlank(traceId) || !SAFE_TRACE_ID.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String resolvedTraceId = traceId;
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(headerName);
            headers.add(headerName, resolvedTraceId);
        }).build();
        exchange.getResponse().getHeaders().set(headerName, resolvedTraceId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
