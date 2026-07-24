package io.devnexus.dynamictp.starter.autoconfigure;

import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.core.DynamicThreadPoolManager;
import io.devnexus.dynamictp.starter.core.ManagedThreadPool;
import io.devnexus.dynamictp.starter.repository.ThreadPoolConfigRepository;
import io.devnexus.dynamictp.starter.model.ThreadPoolSnapshot;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

@Endpoint(id = "dynamicThreadPools")
public class DynamicThreadPoolEndpoint {

    private final DynamicThreadPoolManager manager;
    private final DynamicTpProperties properties;
    private final ThreadPoolConfigRepository threadPoolConfigRepository;

    public DynamicThreadPoolEndpoint(DynamicThreadPoolManager manager, DynamicTpProperties properties,
                                     ThreadPoolConfigRepository threadPoolConfigRepository) {
        this.manager = manager;
        this.properties = properties;
        this.threadPoolConfigRepository = threadPoolConfigRepository;
    }

    @ReadOperation
    public Map<String, Object> pools() {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        Collection<ThreadPoolSnapshot> snapshots = manager.snapshots();
        for (ThreadPoolSnapshot snapshot : snapshots) {
            ManagedThreadPool managedThreadPool = manager.managedThreadPool(snapshot.getPoolName());
            Map<String, Object> pool = new LinkedHashMap<String, Object>();
            pool.put("snapshot", snapshot);
            pool.put("initialConfig", managedThreadPool.getInitialConfig());
            pool.put("currentConfig", managedThreadPool.getCurrentConfig());
            pool.put("latestVersion", managedThreadPool.getLatestVersion());
            pool.put("latestRequestId", managedThreadPool.getLatestRequestId());
            pool.put("latestUpdatedAt", managedThreadPool.getLatestUpdatedAt());
            pool.put("history", managedThreadPool.history(properties.getEndpointHistorySize()));
            pool.put("persistedConfigHistory", threadPoolConfigRepository.history(snapshot.getPoolName(),
                    properties.getEndpointHistorySize()));
            response.put(snapshot.getPoolName(), pool);
        }
        return response;
    }
}