package io.devnexus.dynamictp.starter.metrics;

import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.core.ManagedThreadPool;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DynamicThreadPoolMetrics {

    private final MeterRegistry meterRegistry;
    private final DynamicTpProperties properties;
    private final Map<String, Boolean> boundPools = new ConcurrentHashMap<String, Boolean>();

    public DynamicThreadPoolMetrics(MeterRegistry meterRegistry, DynamicTpProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public void bind(ManagedThreadPool managedThreadPool) {
        if (!properties.isMetricsEnabled()) {
            return;
        }
        if (boundPools.putIfAbsent(managedThreadPool.getPoolName(), Boolean.TRUE) != null) {
            return;
        }

        Gauge.builder("dynamic.tp.pool.size", managedThreadPool, value -> value.getExecutor().getPoolSize())
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.core.size", managedThreadPool, value -> value.getExecutor().getCorePoolSize())
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.max.size", managedThreadPool, value -> value.getExecutor().getMaximumPoolSize())
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.active.count", managedThreadPool, value -> value.getExecutor().getActiveCount())
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.queue.size", managedThreadPool, value -> value.getExecutor().getQueue().size())
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.queue.capacity", managedThreadPool, value -> value.getCurrentConfig().getQueueCapacity())
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.queue.usage", managedThreadPool, value -> queueUsage(value))
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.completed.task.count", managedThreadPool, value -> value.getExecutor().getCompletedTaskCount())
                .baseUnit("tasks")
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
        Gauge.builder("dynamic.tp.keepalive.seconds", managedThreadPool, value -> value.getExecutor().getKeepAliveTime(TimeUnit.SECONDS))
                .tag("pool", managedThreadPool.getPoolName())
                .register(meterRegistry);
    }

    public void recordApplied(String poolName) {
        incrementCounter("dynamic.tp.refresh.applied", poolName);
    }

    public void recordIgnored(String poolName) {
        incrementCounter("dynamic.tp.refresh.ignored", poolName);
    }

    public void recordRejected(String poolName) {
        incrementCounter("dynamic.tp.refresh.rejected", poolName);
    }

    public void recordPersisted(String poolName) {
        incrementCounter("dynamic.tp.config.persisted", poolName);
    }

    public void recordStartupReplay(String poolName) {
        incrementCounter("dynamic.tp.config.startup.replay", poolName);
    }

    public void recordAlert(String poolName) {
        incrementCounter("dynamic.tp.alert.count", poolName);
    }

    private void incrementCounter(String name, String poolName) {
        if (!properties.isMetricsEnabled()) {
            return;
        }
        Counter counter = meterRegistry.counter(name, "pool", poolName);
        counter.increment();
    }

    private double queueUsage(ManagedThreadPool managedThreadPool) {
        int capacity = managedThreadPool.getCurrentConfig().getQueueCapacity();
        if (capacity <= 0) {
            return 0D;
        }
        return (double) managedThreadPool.getExecutor().getQueue().size() / (double) capacity;
    }
}