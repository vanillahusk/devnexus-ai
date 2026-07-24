package com.github.paicoding.forum.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class GatewayRateLimitProperties {

    /** Only trust X-Forwarded-For when the gateway is behind a controlled proxy. */
    private boolean trustedProxy;
}
