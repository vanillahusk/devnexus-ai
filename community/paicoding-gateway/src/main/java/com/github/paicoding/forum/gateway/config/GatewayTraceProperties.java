package com.github.paicoding.forum.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway.trace")
public class GatewayTraceProperties {

    private String headerName = "X-Trace-Id";
}
