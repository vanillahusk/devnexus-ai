package com.github.paicoding.forum.gateway.filter;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.user.dto.BaseUserInfoDTO;
import com.github.paicoding.forum.gateway.config.GatewayAuthProperties;
import com.github.paicoding.forum.gateway.config.GatewayRouteProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GatewayAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final ParameterizedTypeReference<ResVo<BaseUserInfoDTO>> USER_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final GatewayAuthProperties authProperties;
    private final GatewayRouteProperties routeProperties;
    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;
    private final WebClient.Builder webClientBuilder;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getPath();
        if (!authProperties.isEnabled() || isExcludePath(rawPath)) {
            return chain.filter(exchange);
        }

        String session = extractSession(exchange.getRequest());
        if (StringUtils.isBlank(session)) {
            return chain.filter(exchange);
        }

        String targetUrl = buildResolveUserUrl(session, exchange);
        return webClientBuilder.build()
                .get()
                .uri(targetUrl)
                .header(authProperties.getInternalTokenHeader(), authProperties.getInternalToken())
                .retrieve()
                .bodyToMono(USER_RESPONSE_TYPE)
                .flatMap(response -> {
                    if (response == null || response.getStatus() == null
                            || response.getStatus().getCode() != StatusEnum.SUCCESS.getCode()) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                            .header(HttpHeaders.AUTHORIZATION, session)
                            .header("X-Auth-Session", session);
                    if (authProperties.isPropagateUserHeaders() && response.getResult() != null) {
                        BaseUserInfoDTO user = response.getResult();
                        requestBuilder.header("X-Auth-User-Id", String.valueOf(user.getUserId()));
                        if (user.getRole() != null) {
                            requestBuilder.header("X-Auth-User-Role", user.getRole());
                        }
                    }
                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                .onErrorResume(ex -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isExcludePath(String path) {
        for (String pattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String extractSession(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isNotBlank(authHeader)) {
            return authHeader.trim();
        }
        HttpCookie cookie = request.getCookies().getFirst(authProperties.getSessionCookieName());
        return cookie == null ? null : cookie.getValue();
    }

    private String buildResolveUserUrl(String session, ServerWebExchange exchange) {
        String baseUrl = resolveAuthBaseUrl();
        String clientIp = exchange.getRequest().getRemoteAddress() == null
                ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String url = StringUtils.removeEnd(baseUrl, "/") + "/internal/auth/session/resolve?session=" + session;
        return StringUtils.isBlank(clientIp) ? url : url + "&clientIp=" + clientIp;
    }

    private String resolveAuthBaseUrl() {
        String uri = routeProperties.getAuthInternalUri();
        if (StringUtils.startsWithIgnoreCase(uri, "lb://")) {
            String serviceId = StringUtils.removeStartIgnoreCase(uri, "lb://");
            DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
            if (discoveryClient != null) {
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
                if (instances != null && !instances.isEmpty()) {
                    return instances.get(0).getUri().toString();
                }
            }
            return "http://localhost:8093";
        }
        return uri;
    }
}
