package com.github.paicoding.forum.service.discovery;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LocalServiceDiscovery {

    private final LocalServiceDiscoveryProperties properties;
    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public LocalServiceDiscovery(LocalServiceDiscoveryProperties properties,
                                 ObjectProvider<DiscoveryClient> discoveryClientProvider) {
        this.properties = properties;
        this.discoveryClientProvider = discoveryClientProvider;
    }

    public String resolveBaseUrl(String serviceId, String fallbackBaseUrl) {
        if (!properties.isEnabled() || StringUtils.isBlank(serviceId)) {
            return fallbackBaseUrl;
        }
        List<String> instances = resolveRegistryInstances(serviceId);
        if (instances.isEmpty()) {
            instances = resolveStaticInstances(serviceId);
        }
        if (instances.isEmpty()) {
            if (StringUtils.isNotBlank(fallbackBaseUrl)) {
                return fallbackBaseUrl;
            }
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "未找到服务实例: " + serviceId);
        }
        AtomicInteger counter = counters.computeIfAbsent(serviceId, key -> new AtomicInteger(0));
        int index = Math.floorMod(counter.getAndIncrement(), instances.size());
        return instances.get(index);
    }

    private List<String> resolveRegistryInstances(String serviceId) {
        if (!properties.isPreferRegistry()) {
            return List.of();
        }
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient == null) {
            return List.of();
        }
        List<ServiceInstance> serviceInstances = discoveryClient.getInstances(serviceId);
        if (serviceInstances == null || serviceInstances.isEmpty()) {
            return List.of();
        }
        List<String> instances = new ArrayList<>(serviceInstances.size());
        for (ServiceInstance serviceInstance : serviceInstances) {
            URI uri = serviceInstance.getUri();
            if (uri != null) {
                instances.add(uri.toString());
            }
        }
        return instances;
    }

    private List<String> resolveStaticInstances(String serviceId) {
        LocalServiceDiscoveryProperties.ServiceRegistration registration = properties.getServices().get(serviceId);
        if (registration == null || registration.getInstances() == null || registration.getInstances().isEmpty()) {
            return List.of();
        }
        return registration.getInstances();
    }
}
