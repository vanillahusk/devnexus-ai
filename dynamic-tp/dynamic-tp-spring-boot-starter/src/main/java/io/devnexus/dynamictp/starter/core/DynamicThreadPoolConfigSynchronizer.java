package io.devnexus.dynamictp.starter.core;

import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.metrics.DynamicThreadPoolMetrics;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.repository.ThreadPoolConfigRepository;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

public class DynamicThreadPoolConfigSynchronizer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(DynamicThreadPoolConfigSynchronizer.class);

    private final DynamicThreadPoolManager manager;
    private final ThreadPoolConfigRepository threadPoolConfigRepository;
    private final DynamicTpProperties properties;
    private final DynamicThreadPoolMetrics metrics;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public DynamicThreadPoolConfigSynchronizer(DynamicThreadPoolManager manager,
                                               ThreadPoolConfigRepository threadPoolConfigRepository,
                                               DynamicTpProperties properties,
                                               DynamicThreadPoolMetrics metrics) {
        this.manager = manager;
        this.threadPoolConfigRepository = threadPoolConfigRepository;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isSyncConfigOnStartup() || !started.compareAndSet(false, true)) {
            return;
        }

        for (Map.Entry<String, ThreadPoolRefreshCommand> entry : threadPoolConfigRepository.findAll().entrySet()) {
            try {
                manager.refresh(entry.getValue());
                metrics.recordStartupReplay(entry.getKey());
            } catch (Exception exception) {
                log.warn("Failed to replay persisted config for pool {}", entry.getKey(), exception);
            }
        }
    }
}