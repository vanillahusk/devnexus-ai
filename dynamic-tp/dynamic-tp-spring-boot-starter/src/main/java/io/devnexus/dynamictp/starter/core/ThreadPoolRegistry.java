package io.devnexus.dynamictp.starter.core;

import io.devnexus.dynamictp.starter.model.ThreadPoolConfig;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolRegistry {

    private final Map<String, ManagedThreadPool> registry = new ConcurrentHashMap<String, ManagedThreadPool>();

    public void register(String beanName, ThreadPoolExecutor executor) {
        boolean queueResizable = executor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue;
        int queueCapacity = queueResizable
                ? ((ResizableCapacityLinkedBlockingQueue<?>) executor.getQueue()).getCapacity()
                : executor.getQueue().size() + executor.getQueue().remainingCapacity();
        ThreadPoolConfig initialConfig = new ThreadPoolConfig(
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                queueCapacity,
                executor.getKeepAliveTime(java.util.concurrent.TimeUnit.SECONDS));
        registry.put(beanName, new ManagedThreadPool(beanName, executor, initialConfig, queueResizable));
    }

    public ThreadPoolExecutor get(String beanName) {
        ManagedThreadPool managedThreadPool = registry.get(beanName);
        return managedThreadPool == null ? null : managedThreadPool.getExecutor();
    }

    public ManagedThreadPool getManaged(String beanName) {
        return registry.get(beanName);
    }

    public Collection<Map.Entry<String, ManagedThreadPool>> entries() {
        return Collections.unmodifiableCollection(registry.entrySet());
    }
}