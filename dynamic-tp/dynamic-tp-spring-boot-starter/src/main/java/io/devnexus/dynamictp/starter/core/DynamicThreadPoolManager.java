package io.devnexus.dynamictp.starter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devnexus.dynamictp.starter.alert.AlertNotifier;
import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.metrics.DynamicThreadPoolMetrics;
import io.devnexus.dynamictp.starter.model.ThreadPoolChangeRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfig;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.model.ThreadPoolSnapshot;
import io.devnexus.dynamictp.starter.repository.ThreadPoolConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;

public class DynamicThreadPoolManager implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(DynamicThreadPoolManager.class);

    private final ThreadPoolRegistry registry;
    private final ObjectMapper objectMapper;
    private final DynamicTpProperties properties;
    private final List<AlertNotifier> alertNotifiers;
    private final ThreadPoolConfigRepository threadPoolConfigRepository;
    private final DynamicThreadPoolMetrics metrics;
    private final Map<String, Long> lastAlertTimes = new ConcurrentHashMap<String, Long>();

    public DynamicThreadPoolManager(ThreadPoolRegistry registry, ObjectMapper objectMapper,
                                    DynamicTpProperties properties, List<AlertNotifier> alertNotifiers,
                                    ThreadPoolConfigRepository threadPoolConfigRepository,
                                    DynamicThreadPoolMetrics metrics) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.alertNotifiers = alertNotifiers;
        this.threadPoolConfigRepository = threadPoolConfigRepository;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            ThreadPoolRefreshCommand command = objectMapper.readValue(payload, ThreadPoolRefreshCommand.class);
            refresh(command);
        } catch (Exception exception) {
            log.error("Failed to handle thread pool refresh command: {}", payload, exception);
        }
    }

    public void refresh(ThreadPoolRefreshCommand command) {
        command.validate();
        ManagedThreadPool managedThreadPool = registry.getManaged(command.getPoolName());
        if (managedThreadPool == null) {
            throw new IllegalArgumentException("Unknown thread pool: " + command.getPoolName());
        }
        metrics.bind(managedThreadPool);

        String requestId = normalizeRequestId(command);
        long appliedAt = System.currentTimeMillis();
        managedThreadPool.getUpdateLock().lock();
        try {
            if (shouldIgnoreDueToVersion(command, managedThreadPool)) {
                ThreadPoolConfig currentConfig = managedThreadPool.getCurrentConfig();
                managedThreadPool.record(new ThreadPoolChangeRecord(
                        managedThreadPool.getPoolName(), requestId, command.getVersion(), command.getSource(),
                        command.getReason(), "IGNORED", "Ignored stale version command", appliedAt,
                        currentConfig, currentConfig), properties.getChangeHistorySize());
                metrics.recordIgnored(command.getPoolName());
                log.warn("Ignored stale refresh command for pool [{}], requestId={}, version={}, latestVersion={}",
                        command.getPoolName(), requestId, command.getVersion(), managedThreadPool.getLatestVersion());
                return;
            }

            ThreadPoolExecutor executor = managedThreadPool.getExecutor();
            ThreadPoolConfig before = captureConfig(executor);
            ThreadPoolConfig target = resolveTargetConfig(command, managedThreadPool, executor);
            validateTargetConfig(command, managedThreadPool, target, executor);

            safelyResizeExecutor(executor, target.getCoreSize(), target.getMaxSize());
            applyQueueCapacity(managedThreadPool, command, target);
            executor.setKeepAliveTime(target.getKeepAliveSeconds(), TimeUnit.SECONDS);

            managedThreadPool.markApplied(target, command.getVersion(), requestId, appliedAt);
            persistLatestCommand(command, requestId, appliedAt, target);
            managedThreadPool.record(new ThreadPoolChangeRecord(
                    managedThreadPool.getPoolName(), requestId, command.getVersion(), command.getSource(),
                    command.getReason(), "APPLIED", "Refresh command applied successfully", appliedAt,
                    before, target), properties.getChangeHistorySize());
            metrics.recordApplied(command.getPoolName());

            ThreadPoolSnapshot snapshot = snapshot(managedThreadPool);
            log.info("Thread pool [{}] refreshed successfully: requestId={}, version={}, core={}, max={}, queue={}/{}, active={}, completed={}",
                    snapshot.getPoolName(), requestId, command.getVersion(), snapshot.getCoreSize(), snapshot.getMaxSize(),
                    snapshot.getQueueSize(), snapshot.getQueueCapacity(), snapshot.getActiveCount(),
                    snapshot.getCompletedTaskCount());
        } catch (RuntimeException exception) {
            ThreadPoolConfig config = captureConfig(managedThreadPool.getExecutor());
            managedThreadPool.record(new ThreadPoolChangeRecord(
                    managedThreadPool.getPoolName(), requestId, command.getVersion(), command.getSource(),
                    command.getReason(), "REJECTED", exception.getMessage(), appliedAt,
                    config, config), properties.getChangeHistorySize());
            metrics.recordRejected(command.getPoolName());
            throw exception;
        } finally {
            managedThreadPool.getUpdateLock().unlock();
        }
    }

    @Scheduled(fixedDelayString = "${dynamic.tp.monitor-interval-ms:5000}")
    public void monitor() {
        if (!properties.isEnabled()) {
            return;
        }

        for (Map.Entry<String, ManagedThreadPool> entry : registry.entries()) {
            metrics.bind(entry.getValue());
            ThreadPoolSnapshot snapshot = snapshot(entry.getValue());
            if (properties.isMonitorLogEnabled()) {
                log.info("[dynamic-tp-monitor] pool={}, active={}/{}, queue={}/{}, completed={}, totalTasks={}, largestPoolSize={}",
                        snapshot.getPoolName(), snapshot.getActiveCount(), snapshot.getMaxSize(),
                        snapshot.getQueueSize(), snapshot.getQueueCapacity(), snapshot.getCompletedTaskCount(),
                        snapshot.getTaskCount(), snapshot.getLargestPoolSize());
            }

            if (snapshot.getQueueCapacity() > 0 && snapshot.getQueueUsage() >= properties.getAlertThreshold()) {
                sendAlert(snapshot);
            }
        }
    }

    public Collection<ThreadPoolSnapshot> snapshots() {
        List<ThreadPoolSnapshot> snapshots = new ArrayList<ThreadPoolSnapshot>();
        for (Map.Entry<String, ManagedThreadPool> entry : registry.entries()) {
            metrics.bind(entry.getValue());
            snapshots.add(snapshot(entry.getValue()));
        }
        return snapshots;
    }

    public ManagedThreadPool managedThreadPool(String poolName) {
        return registry.getManaged(poolName);
    }

    private void safelyResizeExecutor(ThreadPoolExecutor executor, int targetCore, int targetMax) {
        int currentMax = executor.getMaximumPoolSize();
        if (targetMax > currentMax) {
            executor.setMaximumPoolSize(targetMax);
        }

        executor.setCorePoolSize(targetCore);

        if (targetMax < executor.getMaximumPoolSize()) {
            executor.setMaximumPoolSize(targetMax);
        }
    }

    private void applyQueueCapacity(ManagedThreadPool managedThreadPool, ThreadPoolRefreshCommand command,
                                    ThreadPoolConfig target) {
        if (command.getQueueCapacity() == null) {
            return;
        }

        ThreadPoolExecutor executor = managedThreadPool.getExecutor();
        if (executor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
            ResizableCapacityLinkedBlockingQueue<?> queue = (ResizableCapacityLinkedBlockingQueue<?>) executor.getQueue();
            queue.setCapacity(target.getQueueCapacity());
            return;
        }

        log.warn("Thread pool [{}] does not support dynamic queue resizing", command.getPoolName());
    }

    private ThreadPoolSnapshot snapshot(ManagedThreadPool managedThreadPool) {
        ThreadPoolExecutor executor = managedThreadPool.getExecutor();
        int queueSize = executor.getQueue().size();
        int queueCapacity = queueSize + executor.getQueue().remainingCapacity();
        if (executor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
            queueCapacity = ((ResizableCapacityLinkedBlockingQueue<?>) executor.getQueue()).getCapacity();
        }
        return new ThreadPoolSnapshot(managedThreadPool.getPoolName(), executor.getPoolSize(),
                executor.getCorePoolSize(), executor.getMaximumPoolSize(), executor.getActiveCount(), queueSize,
                queueCapacity, executor.getCompletedTaskCount(), executor.getTaskCount(),
                executor.getLargestPoolSize(), executor.getKeepAliveTime(TimeUnit.SECONDS),
                managedThreadPool.isQueueResizable());
    }

    private void sendAlert(ThreadPoolSnapshot snapshot) {
        long now = System.currentTimeMillis();
        Long lastTime = lastAlertTimes.get(snapshot.getPoolName());
        if (lastTime != null && now - lastTime < properties.getAlertCooldownMs()) {
            return;
        }
        lastAlertTimes.put(snapshot.getPoolName(), now);
        metrics.recordAlert(snapshot.getPoolName());

        String title = "Dynamic Thread Pool Alert";
        String content = String.format("pool=%s queue usage=%.2f%% active=%d/%d queue=%d/%d completed=%d keepAliveSeconds=%d",
                snapshot.getPoolName(), snapshot.getQueueUsage() * 100D, snapshot.getActiveCount(),
                snapshot.getMaxSize(), snapshot.getQueueSize(), snapshot.getQueueCapacity(),
                snapshot.getCompletedTaskCount(), snapshot.getKeepAliveSeconds());
        for (AlertNotifier alertNotifier : alertNotifiers) {
            alertNotifier.send(title, content);
        }
    }

    private String normalizeRequestId(ThreadPoolRefreshCommand command) {
        if (command.getRequestId() != null && command.getRequestId().trim().length() > 0) {
            return command.getRequestId().trim();
        }
        return command.getPoolName() + "-" + System.currentTimeMillis();
    }

    private boolean shouldIgnoreDueToVersion(ThreadPoolRefreshCommand command, ManagedThreadPool managedThreadPool) {
        return properties.isRejectStaleVersion()
                && command.getVersion() != null
                && managedThreadPool.getLatestVersion() != null
                && command.getVersion().longValue() <= managedThreadPool.getLatestVersion().longValue();
    }

    private ThreadPoolConfig resolveTargetConfig(ThreadPoolRefreshCommand command, ManagedThreadPool managedThreadPool,
                                                 ThreadPoolExecutor executor) {
        ThreadPoolConfig current = managedThreadPool.getCurrentConfig();
        int queueCapacity = command.getQueueCapacity() != null
                ? command.getQueueCapacity().intValue()
                : current.getQueueCapacity();
        return new ThreadPoolConfig(
                command.getCoreSize() != null ? command.getCoreSize().intValue() : executor.getCorePoolSize(),
                command.getMaxSize() != null ? command.getMaxSize().intValue() : executor.getMaximumPoolSize(),
                queueCapacity,
                command.getKeepAliveSeconds() != null ? command.getKeepAliveSeconds().longValue()
                        : executor.getKeepAliveTime(TimeUnit.SECONDS));
    }

    private void validateTargetConfig(ThreadPoolRefreshCommand command, ManagedThreadPool managedThreadPool,
                                      ThreadPoolConfig target, ThreadPoolExecutor executor) {
        if (target.getCoreSize() > target.getMaxSize()) {
            throw new IllegalArgumentException("coreSize cannot be greater than maxSize");
        }
        if (target.getCoreSize() > properties.getMaxCoreSize()) {
            throw new IllegalArgumentException("coreSize exceeds safety limit: " + properties.getMaxCoreSize());
        }
        if (target.getMaxSize() > properties.getMaxPoolSize()) {
            throw new IllegalArgumentException("maxSize exceeds safety limit: " + properties.getMaxPoolSize());
        }
        if (target.getQueueCapacity() > properties.getMaxQueueCapacity()) {
            throw new IllegalArgumentException("queueCapacity exceeds safety limit: "
                    + properties.getMaxQueueCapacity());
        }
        if (command.getQueueCapacity() != null) {
            if (!managedThreadPool.isQueueResizable()) {
                throw new IllegalArgumentException("Thread pool does not support dynamic queue resizing");
            }
            if (!properties.isAllowQueueCapacityShrink() && target.getQueueCapacity() < executor.getQueue().size()) {
                throw new IllegalArgumentException("queueCapacity cannot be smaller than current queue size");
            }
        }
    }

    private ThreadPoolConfig captureConfig(ThreadPoolExecutor executor) {
        int queueCapacity = executor.getQueue().size() + executor.getQueue().remainingCapacity();
        if (executor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
            queueCapacity = ((ResizableCapacityLinkedBlockingQueue<?>) executor.getQueue()).getCapacity();
        }
        return new ThreadPoolConfig(executor.getCorePoolSize(), executor.getMaximumPoolSize(), queueCapacity,
                executor.getKeepAliveTime(TimeUnit.SECONDS));
    }

    private void persistLatestCommand(ThreadPoolRefreshCommand command, String requestId, long appliedAt,
                                      ThreadPoolConfig target) {
        ThreadPoolRefreshCommand persisted = new ThreadPoolRefreshCommand();
        persisted.setPoolName(command.getPoolName());
        persisted.setRequestId(requestId);
        persisted.setVersion(command.getVersion());
        persisted.setSource(command.getSource());
        persisted.setReason(command.getReason());
        persisted.setCoreSize(target.getCoreSize());
        persisted.setMaxSize(target.getMaxSize());
        persisted.setQueueCapacity(target.getQueueCapacity());
        persisted.setKeepAliveSeconds(target.getKeepAliveSeconds());
        persisted.setTimestamp(command.getTimestamp() != null ? command.getTimestamp() : appliedAt);
        persisted.setRollback(command.getRollback());
        persisted.setRollbackFromVersion(command.getRollbackFromVersion());
        threadPoolConfigRepository.save(persisted);
        metrics.recordPersisted(command.getPoolName());
    }
}
