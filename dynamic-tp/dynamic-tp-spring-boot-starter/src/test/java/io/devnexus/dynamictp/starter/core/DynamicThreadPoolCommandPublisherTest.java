package io.devnexus.dynamictp.starter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.metrics.DynamicThreadPoolMetrics;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfigVersionRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.model.ThreadPoolRollbackRequest;
import io.devnexus.dynamictp.starter.repository.ThreadPoolConfigRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class DynamicThreadPoolCommandPublisherTest {

    @Test
    void shouldPublishRollbackByTargetVersion() throws Exception {
        CapturingStringRedisTemplate template = new CapturingStringRedisTemplate();
        InMemoryRepository repository = new InMemoryRepository();
        DynamicTpProperties properties = new DynamicTpProperties();
        properties.setRedisChannel("dynamic-tp:refresh");

        DynamicTpProperties metricsProps = new DynamicTpProperties();
        metricsProps.setMetricsEnabled(false);
        DynamicThreadPoolMetrics metrics = new DynamicThreadPoolMetrics(new SimpleMeterRegistry(), metricsProps);

        DynamicThreadPoolCommandPublisher publisher = new DynamicThreadPoolCommandPublisher(
                template,
                new ObjectMapper(),
                properties,
                repository,
                metrics);

        ThreadPoolConfigVersionRecord base = new ThreadPoolConfigVersionRecord();
        base.setPoolName("orderThreadPool");
        base.setVersion(1001L);
        base.setRequestId("req-1001");
        base.setSource("test");
        base.setReason("base");
        base.setCoreSize(6);
        base.setMaxSize(12);
        base.setQueueCapacity(64);
        base.setKeepAliveSeconds(120L);
        base.setTimestamp(System.currentTimeMillis());
        repository.put(base);

        ThreadPoolRollbackRequest rollbackRequest = new ThreadPoolRollbackRequest();
        rollbackRequest.setPoolName("orderThreadPool");
        rollbackRequest.setTargetVersion(1001L);
        rollbackRequest.setVersion(2001L);
        rollbackRequest.setRequestId("rollback-2001");
        rollbackRequest.setSource("rollback-test");
        rollbackRequest.setReason("restore");
        rollbackRequest.setTimestamp(System.currentTimeMillis());

        publisher.rollback(rollbackRequest);

        Assertions.assertEquals("dynamic-tp:refresh", template.channel());
        ThreadPoolRefreshCommand persisted = repository.latest("orderThreadPool");
        Assertions.assertNull(persisted);
        Assertions.assertNotNull(template.payload());
        Assertions.assertTrue(template.payload().contains("\"rollback\":true"));
    }

    private static class CapturingStringRedisTemplate extends StringRedisTemplate {
        private String channel;
        private String payload;

        @Override
        public Long convertAndSend(String channel, Object message) {
            this.channel = channel;
            this.payload = String.valueOf(message);
            return 1L;
        }

        String channel() {
            return channel;
        }

        String payload() {
            return payload;
        }
    }

    private static class InMemoryRepository implements ThreadPoolConfigRepository {

        private final Map<String, ThreadPoolRefreshCommand> latest = new HashMap<String, ThreadPoolRefreshCommand>();
        private final Map<String, ThreadPoolConfigVersionRecord> versions = new HashMap<String, ThreadPoolConfigVersionRecord>();

        @Override
        public void save(ThreadPoolRefreshCommand command) {
            latest.put(command.getPoolName(), copy(command));
            versions.put(command.getPoolName() + ":" + command.getVersion(),
                    ThreadPoolConfigVersionRecord.fromCommand(command, "ACTIVE"));
        }

        @Override
        public ThreadPoolRefreshCommand find(String poolName) {
            return latest.get(poolName);
        }

        @Override
        public Map<String, ThreadPoolRefreshCommand> findAll() {
            return Collections.unmodifiableMap(latest);
        }

        @Override
        public ThreadPoolConfigVersionRecord findVersion(String poolName, Long version) {
            return versions.get(poolName + ":" + version);
        }

        @Override
        public List<ThreadPoolConfigVersionRecord> history(String poolName, int limit) {
            return Collections.emptyList();
        }

        ThreadPoolRefreshCommand latest(String poolName) {
            return latest.get(poolName);
        }

        void put(ThreadPoolConfigVersionRecord record) {
            versions.put(record.getPoolName() + ":" + record.getVersion(), record);
        }

        private ThreadPoolRefreshCommand copy(ThreadPoolRefreshCommand source) {
            ThreadPoolRefreshCommand copy = new ThreadPoolRefreshCommand();
            copy.setRequestId(source.getRequestId());
            copy.setVersion(source.getVersion());
            copy.setPoolName(source.getPoolName());
            copy.setSource(source.getSource());
            copy.setReason(source.getReason());
            copy.setCoreSize(source.getCoreSize());
            copy.setMaxSize(source.getMaxSize());
            copy.setQueueCapacity(source.getQueueCapacity());
            copy.setKeepAliveSeconds(source.getKeepAliveSeconds());
            copy.setTimestamp(source.getTimestamp());
            copy.setRollback(source.getRollback());
            copy.setRollbackFromVersion(source.getRollbackFromVersion());
            return copy;
        }
    }
}
