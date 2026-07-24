package com.github.paicoding.forum.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway.routes")
public class GatewayRouteProperties {

    private String forumServiceId = "forum-service";

    private String authServiceId = "auth-service";

    private String aigcServiceId = "aigc-service";

    private String messageServiceId = "message-service";

    /**
     * 网关鉴权过滤器调用认证服务的地址。
     * 本地开发默认直连；启用 Nacos 后可以配置为 lb://auth-service。
     */
    private String authInternalUri = "http://localhost:8093";
}
