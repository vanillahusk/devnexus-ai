package com.github.paicoding.forum.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    private boolean enabled = true;

    private List<String> excludePaths = new ArrayList<>();

    private String sessionCookieName = "f-session";

    private String internalTokenHeader = "X-AUTH-INTERNAL-TOKEN";

    private String internalToken = "paicoding-auth-dev-token";

    private boolean propagateUserHeaders = true;
}
