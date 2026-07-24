package com.github.paicoding.forum.web.config;

import io.devnexus.dynamictp.starter.core.ResizableCapacityLinkedBlockingQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class FavorThreadPoolConfig {

    @Bean("favorNotifyExecutor")
    public ExecutorService favorNotifyExecutor(MeterRegistry meterRegistry) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4,
                12,
                60L,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(1024),
                new FavorRejectAwareHandler("favorNotifyExecutor", meterRegistry));
        registerPoolMetrics(meterRegistry, "favorNotifyExecutor", executor);
        return executor;
    }

    @Bean("favorPersistExecutor")
    public ExecutorService favorPersistExecutor(MeterRegistry meterRegistry) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4,
                16,
                60L,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(2048),
                new FavorRejectAwareHandler("favorPersistExecutor", meterRegistry));
        registerPoolMetrics(meterRegistry, "favorPersistExecutor", executor);
        return executor;
    }

    private void registerPoolMetrics(MeterRegistry meterRegistry, String poolName, ThreadPoolExecutor executor) {
        Gauge.builder("favor.executor.active.count", executor, ThreadPoolExecutor::getActiveCount)
                .tag("pool", poolName)
                .register(meterRegistry);
        Gauge.builder("favor.executor.pool.size", executor, ThreadPoolExecutor::getPoolSize)
                .tag("pool", poolName)
                .register(meterRegistry);
        Gauge.builder("favor.executor.queue.size", executor, e -> e.getQueue().size())
                .tag("pool", poolName)
                .register(meterRegistry);
        Gauge.builder("favor.executor.queue.usage", executor, e -> {
                    BlockingQueue<Runnable> queue = e.getQueue();
                    int size = queue.size();
                    int capacity = size + queue.remainingCapacity();
                    if (capacity <= 0) {
                        return 0D;
                    }
                    return (double) size / capacity;
                })
                .tag("pool", poolName)
                .register(meterRegistry);
    }
}
