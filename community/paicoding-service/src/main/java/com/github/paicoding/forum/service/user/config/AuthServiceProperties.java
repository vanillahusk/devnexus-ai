package com.github.paicoding.forum.service.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.service")
public class AuthServiceProperties {

    /**
     * 认证服务调用模式：local / remote
     */
    private String mode = "local";

    /**
     * 远端认证服务地址
     */
    private String baseUrl = "http://localhost:8093";

    /**
     * 认证服务 serviceId
     */
    private String serviceId = "auth-service";

    /**
     * 认证内部接口前缀
     */
    private String internalPath = "/internal/auth";

    /**
     * 服务间调用 token header
     */
    private String tokenHeader = "X-AUTH-INTERNAL-TOKEN";

    /**
     * 服务间调用 token
     */
    private String token;
}
