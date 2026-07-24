package com.github.paicoding.forum.service.discovery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "service.discovery")
public class LocalServiceDiscoveryProperties {

    /**
     * 是否启用项目内服务发现
     */
    private boolean enabled = true;

    /**
     * 是否优先使用 Nacos/DiscoveryClient 返回的实例
     */
    private boolean preferRegistry = true;

    /**
     * serviceId -> 实例列表
     */
    private Map<String, ServiceRegistration> services = new LinkedHashMap<>();

    @Data
    public static class ServiceRegistration {
        private List<String> instances = new ArrayList<>();
    }
}
