package io.devnexus.dynamictp.starter.autoconfigure;

import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.core.DynamicThreadPoolManager;
import io.devnexus.dynamictp.starter.model.ThreadPoolSnapshot;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

public class DynamicThreadPoolHealthIndicator implements HealthIndicator {

    private final DynamicThreadPoolManager manager;
    private final DynamicTpProperties properties;

    public DynamicThreadPoolHealthIndicator(DynamicThreadPoolManager manager, DynamicTpProperties properties) {
        this.manager = manager;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Collection<ThreadPoolSnapshot> snapshots = manager.snapshots();
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        boolean critical = false;
        for (ThreadPoolSnapshot snapshot : snapshots) {
            Map<String, Object> pool = new LinkedHashMap<String, Object>();
            pool.put("queueUsage", snapshot.getQueueUsage());
            pool.put("activeUsage", snapshot.getActiveUsage());
            pool.put("queueSize", snapshot.getQueueSize());
            pool.put("queueCapacity", snapshot.getQueueCapacity());
            details.put(snapshot.getPoolName(), pool);
            if (snapshot.getQueueCapacity() > 0 && snapshot.getQueueUsage() >= properties.getCriticalThreshold()) {
                critical = true;
            }
        }
        return critical ? Health.status(Status.DOWN).withDetails(details).build() : Health.up().withDetails(details).build();
    }
}