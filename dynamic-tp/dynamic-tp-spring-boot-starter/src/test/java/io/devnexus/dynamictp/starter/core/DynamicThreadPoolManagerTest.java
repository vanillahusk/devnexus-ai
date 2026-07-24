package io.devnexus.dynamictp.starter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devnexus.dynamictp.starter.alert.AlertNotifier;
import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.metrics.DynamicThreadPoolMetrics;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.repository.NoopThreadPoolConfigRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DynamicThreadPoolManagerTest {

    private ThreadPoolExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldIgnoreStaleVersionCommand() {
        DynamicThreadPoolManager manager = newManager();

        ThreadPoolRefreshCommand first = command(2L, 3, 6, 20);
        manager.refresh(first);

        ThreadPoolRefreshCommand stale = command(1L, 8, 10, 30);
        manager.refresh(stale);

        Assertions.assertEquals(3, executor.getCorePoolSize());
        Assertions.assertEquals(6, executor.getMaximumPoolSize());
        Assertions.assertEquals(20,
                ((ResizableCapacityLinkedBlockingQueue<?>) executor.getQueue()).getCapacity());
    }

    @Test
    void shouldRejectUnsafeQueueShrink() {
        DynamicThreadPoolManager manager = newManager();
        executor.getQueue().offer(new Runnable() {
            @Override
            public void run() {
            }
        });
        executor.getQueue().offer(new Runnable() {
            @Override
            public void run() {
            }
        });

        ThreadPoolRefreshCommand command = command(1L, 2, 5, 1);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        manager.refresh(command);
                    }
                });

        Assertions.assertTrue(exception.getMessage().contains("queueCapacity cannot be smaller"));
    }

    @Test
    void shouldRejectConfigurationBeyondSafetyLimit() {
        DynamicThreadPoolManager manager = newManager();

        ThreadPoolRefreshCommand command = command(1L, 129, 200, 1000);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        manager.refresh(command);
                    }
                });

        Assertions.assertTrue(exception.getMessage().contains("coreSize exceeds safety limit"));
        Assertions.assertEquals(2, executor.getCorePoolSize());
        Assertions.assertEquals(5, executor.getMaximumPoolSize());
    }

    private DynamicThreadPoolManager newManager() {
        executor = new ThreadPoolExecutor(2, 5, 60L, TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<Runnable>(10));
        ThreadPoolRegistry registry = new ThreadPoolRegistry();
        registry.register("orderThreadPool", executor);
        DynamicTpProperties properties = new DynamicTpProperties();
        properties.setRejectStaleVersion(true);
        properties.setAllowQueueCapacityShrink(false);
        AlertNotifier notifier = new AlertNotifier() {
            @Override
            public void send(String title, String content) {
            }
        };
        DynamicTpProperties metricProperties = new DynamicTpProperties();
        metricProperties.setMetricsEnabled(false);
        return new DynamicThreadPoolManager(registry, new ObjectMapper(), properties,
            Collections.singletonList(notifier), new NoopThreadPoolConfigRepository(),
            new DynamicThreadPoolMetrics(new SimpleMeterRegistry(), metricProperties));
    }

    private ThreadPoolRefreshCommand command(Long version, Integer coreSize, Integer maxSize, Integer queueCapacity) {
        ThreadPoolRefreshCommand command = new ThreadPoolRefreshCommand();
        command.setPoolName("orderThreadPool");
        command.setRequestId("req-" + version);
        command.setVersion(version);
        command.setSource("test");
        command.setCoreSize(coreSize);
        command.setMaxSize(maxSize);
        command.setQueueCapacity(queueCapacity);
        return command;
    }
}
