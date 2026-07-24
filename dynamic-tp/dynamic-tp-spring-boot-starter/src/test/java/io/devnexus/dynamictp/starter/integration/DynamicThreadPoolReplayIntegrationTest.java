package io.devnexus.dynamictp.starter.integration;

import io.devnexus.dynamictp.starter.core.ResizableCapacityLinkedBlockingQueue;
import io.devnexus.dynamictp.starter.core.DynamicThreadPoolManager;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfigVersionRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.repository.ThreadPoolConfigRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

class DynamicThreadPoolReplayIntegrationTest {

    @AfterEach
    void clearRepository() {
        InMemoryThreadPoolConfigRepository.clear();
    }

    @Test
    void shouldReplayPersistedConfigWhenNewInstanceStarts() {
        ConfigurableApplicationContext first = new SpringApplicationBuilder(TestReplayApplication.class)
                .web(WebApplicationType.NONE)
                .properties("dynamic.tp.monitor-log-enabled=false")
                .run();

        try {
            DynamicThreadPoolManager manager = first.getBean(DynamicThreadPoolManager.class);
            ThreadPoolRefreshCommand command = new ThreadPoolRefreshCommand();
            command.setPoolName("orderThreadPool");
            command.setRequestId("instance-a-1001");
            command.setVersion(1001L);
            command.setSource("instance-a");
            command.setReason("promotion-traffic");
            command.setCoreSize(6);
            command.setMaxSize(12);
            command.setQueueCapacity(64);
            command.setKeepAliveSeconds(120L);
            manager.refresh(command);

            ThreadPoolExecutor firstPool = first.getBean("orderThreadPool", ThreadPoolExecutor.class);
            Assertions.assertEquals(6, firstPool.getCorePoolSize());
            Assertions.assertEquals(12, firstPool.getMaximumPoolSize());
            Assertions.assertEquals(64,
                    ((ResizableCapacityLinkedBlockingQueue<?>) firstPool.getQueue()).getCapacity());
        } finally {
            first.close();
        }

        ConfigurableApplicationContext second = new SpringApplicationBuilder(TestReplayApplication.class)
                .web(WebApplicationType.NONE)
                .properties("dynamic.tp.monitor-log-enabled=false")
                .run();

        try {
            ThreadPoolExecutor secondPool = second.getBean("orderThreadPool", ThreadPoolExecutor.class);
            Assertions.assertEquals(6, secondPool.getCorePoolSize());
            Assertions.assertEquals(12, secondPool.getMaximumPoolSize());
            Assertions.assertEquals(64,
                    ((ResizableCapacityLinkedBlockingQueue<?>) secondPool.getQueue()).getCapacity());
            Assertions.assertEquals(120L, secondPool.getKeepAliveTime(TimeUnit.SECONDS));

            DynamicThreadPoolManager secondManager = second.getBean(DynamicThreadPoolManager.class);
            Assertions.assertEquals(Long.valueOf(1001L),
                    secondManager.managedThreadPool("orderThreadPool").getLatestVersion());
        } finally {
            second.close();
        }
    }

    @Test
    void shouldRollbackToHistoricalVersion() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TestReplayApplication.class)
                .web(WebApplicationType.NONE)
                .properties("dynamic.tp.monitor-log-enabled=false")
                .run();

        try {
            DynamicThreadPoolManager manager = context.getBean(DynamicThreadPoolManager.class);
            manager.refresh(command(1001L, 6, 12, 64, 120L));
            manager.refresh(command(1002L, 8, 16, 80, 180L));

            ThreadPoolConfigRepository repository = context.getBean(ThreadPoolConfigRepository.class);
            ThreadPoolConfigVersionRecord version1001 = repository.findVersion("orderThreadPool", 1001L);
            Assertions.assertNotNull(version1001);

            ThreadPoolRefreshCommand rollback = version1001.toRefreshCommand();
            rollback.setRequestId("rollback-2001");
            rollback.setVersion(2001L);
            rollback.setSource("test-rollback");
            rollback.setReason("restore-stable-profile");
            rollback.setTimestamp(System.currentTimeMillis());
            rollback.setRollback(Boolean.TRUE);
            rollback.setRollbackFromVersion(1001L);
            manager.refresh(rollback);

            ThreadPoolExecutor pool = context.getBean("orderThreadPool", ThreadPoolExecutor.class);
            Assertions.assertEquals(6, pool.getCorePoolSize());
            Assertions.assertEquals(12, pool.getMaximumPoolSize());
            Assertions.assertEquals(64,
                    ((ResizableCapacityLinkedBlockingQueue<?>) pool.getQueue()).getCapacity());

            ThreadPoolConfigVersionRecord latest = repository.findVersion("orderThreadPool", 2001L);
            Assertions.assertNotNull(latest);
            Assertions.assertEquals(Boolean.TRUE, latest.getRollback());
            Assertions.assertEquals(Long.valueOf(1001L), latest.getRollbackFromVersion());
        } finally {
            context.close();
        }
    }

    @SpringBootApplication
    static class TestReplayApplication {

        @Bean
        public ThreadPoolExecutor orderThreadPool() {
            return new ThreadPoolExecutor(
                    2,
                    5,
                    60,
                    TimeUnit.SECONDS,
                    new ResizableCapacityLinkedBlockingQueue<Runnable>(10),
                    new ThreadPoolExecutor.AbortPolicy());
        }

        @Bean
        @Primary
        public ThreadPoolConfigRepository threadPoolConfigRepository() {
            return InMemoryThreadPoolConfigRepository.INSTANCE;
        }
    }

    static class InMemoryThreadPoolConfigRepository implements ThreadPoolConfigRepository {

        private static final InMemoryThreadPoolConfigRepository INSTANCE = new InMemoryThreadPoolConfigRepository();

        private final Map<String, ThreadPoolRefreshCommand> store = new ConcurrentHashMap<String, ThreadPoolRefreshCommand>();
        private final Map<String, ThreadPoolConfigVersionRecord> versions = new ConcurrentHashMap<String, ThreadPoolConfigVersionRecord>();

        static void clear() {
            INSTANCE.store.clear();
            INSTANCE.versions.clear();
        }

        @Override
        public void save(ThreadPoolRefreshCommand command) {
            store.put(command.getPoolName(), copy(command));
            versions.put(versionField(command.getPoolName(), command.getVersion()),
                    ThreadPoolConfigVersionRecord.fromCommand(copy(command), "ACTIVE"));
        }

        @Override
        public ThreadPoolRefreshCommand find(String poolName) {
            ThreadPoolRefreshCommand command = store.get(poolName);
            return command == null ? null : copy(command);
        }

        @Override
        public Map<String, ThreadPoolRefreshCommand> findAll() {
            Map<String, ThreadPoolRefreshCommand> copy = new LinkedHashMap<String, ThreadPoolRefreshCommand>();
            for (Map.Entry<String, ThreadPoolRefreshCommand> entry : store.entrySet()) {
                copy.put(entry.getKey(), copy(entry.getValue()));
            }
            return copy;
        }

        @Override
        public ThreadPoolConfigVersionRecord findVersion(String poolName, Long version) {
            ThreadPoolConfigVersionRecord record = versions.get(versionField(poolName, version));
            return record == null ? null : copy(record);
        }

        @Override
        public List<ThreadPoolConfigVersionRecord> history(String poolName, int limit) {
            List<ThreadPoolConfigVersionRecord> history = new ArrayList<ThreadPoolConfigVersionRecord>();
            for (ThreadPoolConfigVersionRecord record : versions.values()) {
                if (poolName.equals(record.getPoolName())) {
                    history.add(copy(record));
                }
            }
            java.util.Collections.sort(history, new java.util.Comparator<ThreadPoolConfigVersionRecord>() {
                @Override
                public int compare(ThreadPoolConfigVersionRecord left, ThreadPoolConfigVersionRecord right) {
                    return right.getVersion().compareTo(left.getVersion());
                }
            });
            return history.size() > limit ? history.subList(0, limit) : history;
        }

        private ThreadPoolRefreshCommand copy(ThreadPoolRefreshCommand source) {
            ThreadPoolRefreshCommand target = new ThreadPoolRefreshCommand();
            target.setRequestId(source.getRequestId());
            target.setVersion(source.getVersion());
            target.setPoolName(source.getPoolName());
            target.setSource(source.getSource());
            target.setReason(source.getReason());
            target.setCoreSize(source.getCoreSize());
            target.setMaxSize(source.getMaxSize());
            target.setQueueCapacity(source.getQueueCapacity());
            target.setKeepAliveSeconds(source.getKeepAliveSeconds());
            target.setTimestamp(source.getTimestamp());
            target.setRollback(source.getRollback());
            target.setRollbackFromVersion(source.getRollbackFromVersion());
            return target;
        }

        private ThreadPoolConfigVersionRecord copy(ThreadPoolConfigVersionRecord source) {
            ThreadPoolConfigVersionRecord target = new ThreadPoolConfigVersionRecord();
            target.setPoolName(source.getPoolName());
            target.setRequestId(source.getRequestId());
            target.setVersion(source.getVersion());
            target.setSource(source.getSource());
            target.setReason(source.getReason());
            target.setCoreSize(source.getCoreSize());
            target.setMaxSize(source.getMaxSize());
            target.setQueueCapacity(source.getQueueCapacity());
            target.setKeepAliveSeconds(source.getKeepAliveSeconds());
            target.setTimestamp(source.getTimestamp());
            target.setRollback(source.getRollback());
            target.setRollbackFromVersion(source.getRollbackFromVersion());
            target.setState(source.getState());
            return target;
        }

        private String versionField(String poolName, Long version) {
            return poolName + ":" + version;
        }
    }

    private ThreadPoolRefreshCommand command(Long version, Integer coreSize, Integer maxSize,
                                             Integer queueCapacity, Long keepAliveSeconds) {
        ThreadPoolRefreshCommand command = new ThreadPoolRefreshCommand();
        command.setPoolName("orderThreadPool");
        command.setRequestId("request-" + version);
        command.setVersion(version);
        command.setSource("test-replay");
        command.setReason("load-shift");
        command.setCoreSize(coreSize);
        command.setMaxSize(maxSize);
        command.setQueueCapacity(queueCapacity);
        command.setKeepAliveSeconds(keepAliveSeconds);
        command.setTimestamp(System.currentTimeMillis());
        return command;
    }
}