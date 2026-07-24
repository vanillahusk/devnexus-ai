package io.devnexus.dynamictp.starter.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.metrics.DynamicThreadPoolMetrics;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfigVersionRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.model.ThreadPoolRollbackRequest;
import java.util.List;
import io.devnexus.dynamictp.starter.repository.ThreadPoolConfigRepository;
import org.springframework.data.redis.core.StringRedisTemplate;

public class DynamicThreadPoolCommandPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DynamicTpProperties properties;
    private final ThreadPoolConfigRepository threadPoolConfigRepository;
    private final DynamicThreadPoolMetrics metrics;

    public DynamicThreadPoolCommandPublisher(StringRedisTemplate stringRedisTemplate,
                                             ObjectMapper objectMapper,
                                             DynamicTpProperties properties,
                                             ThreadPoolConfigRepository threadPoolConfigRepository,
                                             DynamicThreadPoolMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.threadPoolConfigRepository = threadPoolConfigRepository;
        this.metrics = metrics;
    }

    public String publish(ThreadPoolRefreshCommand command) throws JsonProcessingException {
        command.validate();
        String payload = objectMapper.writeValueAsString(command);
        stringRedisTemplate.convertAndSend(properties.getRedisChannel(), payload);
        return payload;
    }

    public String rollback(ThreadPoolRollbackRequest rollbackRequest) throws JsonProcessingException {
        rollbackRequest.validate();
        ThreadPoolConfigVersionRecord target = threadPoolConfigRepository.findVersion(
                rollbackRequest.getPoolName(), rollbackRequest.getTargetVersion());
        if (target == null) {
            throw new IllegalArgumentException("Unknown config version: pool=" + rollbackRequest.getPoolName()
                    + ", version=" + rollbackRequest.getTargetVersion());
        }

        ThreadPoolRefreshCommand command = target.toRefreshCommand();
        command.setRequestId(rollbackRequest.getRequestId());
        command.setVersion(rollbackRequest.getVersion());
        command.setSource(rollbackRequest.getSource());
        command.setReason(rollbackRequest.getReason());
        command.setTimestamp(rollbackRequest.getTimestamp());
        command.setRollback(Boolean.TRUE);
        command.setRollbackFromVersion(rollbackRequest.getTargetVersion());
        return publish(command);
    }

    public List<ThreadPoolConfigVersionRecord> history(String poolName, int limit) {
        return threadPoolConfigRepository.history(poolName, limit);
    }
}
