package io.devnexus.dynamictp.starter.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devnexus.dynamictp.starter.config.DynamicTpProperties;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfigVersionRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

public class RedisThreadPoolConfigRepository implements ThreadPoolConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisThreadPoolConfigRepository.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DynamicTpProperties properties;

    public RedisThreadPoolConfigRepository(StringRedisTemplate stringRedisTemplate,
                                           ObjectMapper objectMapper,
                                           DynamicTpProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void save(ThreadPoolRefreshCommand command) {
        if (!properties.isConfigCacheEnabled()) {
            return;
        }
        try {
            ThreadPoolConfigVersionRecord record = ThreadPoolConfigVersionRecord.fromCommand(command, "ACTIVE");
            String latestKey = latestKey();
            String versionsKey = versionsKey();
            String timelineKey = timelineKey(command.getPoolName());
            String versionField = versionField(command.getPoolName(), command.getVersion());
            String recordJson = objectMapper.writeValueAsString(record);
            stringRedisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    operations.multi();
                    operations.opsForHash().put(latestKey, command.getPoolName(), recordJson);
                    operations.opsForHash().put(versionsKey, versionField, recordJson);
                    operations.opsForZSet().add(timelineKey, versionField,
                            command.getVersion() == null ? System.currentTimeMillis() : command.getVersion().doubleValue());
                    return operations.exec();
                }
            });
            trimHistory(command.getPoolName(), timelineKey, versionsKey);
        } catch (Exception exception) {
            log.warn("Failed to persist thread pool config for pool {}", command.getPoolName(), exception);
        }
    }

    @Override
    public ThreadPoolRefreshCommand find(String poolName) {
        if (!properties.isConfigCacheEnabled() || !StringUtils.hasText(poolName)) {
            return null;
        }
        try {
            Object payload = stringRedisTemplate.opsForHash().get(latestKey(), poolName);
            if (payload == null) {
                return null;
            }
            return objectMapper.readValue(String.valueOf(payload), ThreadPoolConfigVersionRecord.class).toRefreshCommand();
        } catch (Exception exception) {
            log.warn("Failed to load thread pool config for pool {}", poolName, exception);
            return null;
        }
    }

    @Override
    public Map<String, ThreadPoolRefreshCommand> findAll() {
        if (!properties.isConfigCacheEnabled()) {
            return Collections.emptyMap();
        }
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(latestKey());
            Map<String, ThreadPoolRefreshCommand> commands = new LinkedHashMap<String, ThreadPoolRefreshCommand>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                commands.put(String.valueOf(entry.getKey()), objectMapper.readValue(String.valueOf(entry.getValue()),
                        ThreadPoolConfigVersionRecord.class).toRefreshCommand());
            }
            return commands;
        } catch (Exception exception) {
            log.warn("Failed to load thread pool configs from Redis key {}", latestKey(), exception);
            return Collections.emptyMap();
        }
    }

    @Override
    public ThreadPoolConfigVersionRecord findVersion(String poolName, Long version) {
        if (!properties.isConfigCacheEnabled() || !StringUtils.hasText(poolName) || version == null) {
            return null;
        }
        try {
            Object payload = stringRedisTemplate.opsForHash().get(versionsKey(), versionField(poolName, version));
            if (payload == null) {
                return null;
            }
            return objectMapper.readValue(String.valueOf(payload), ThreadPoolConfigVersionRecord.class);
        } catch (Exception exception) {
            log.warn("Failed to load thread pool config version for pool {}, version={}", poolName, version, exception);
            return null;
        }
    }

    @Override
    public List<ThreadPoolConfigVersionRecord> history(String poolName, int limit) {
        if (!properties.isConfigCacheEnabled() || !StringUtils.hasText(poolName) || limit <= 0) {
            return Collections.emptyList();
        }
        try {
            Set<String> versionFields = stringRedisTemplate.opsForZSet().reverseRange(timelineKey(poolName), 0, limit - 1);
            if (versionFields == null || versionFields.isEmpty()) {
                return Collections.emptyList();
            }

            List<ThreadPoolConfigVersionRecord> history = new ArrayList<ThreadPoolConfigVersionRecord>();
            for (String versionField : versionFields) {
                Object payload = stringRedisTemplate.opsForHash().get(versionsKey(), versionField);
                if (payload != null) {
                    history.add(objectMapper.readValue(String.valueOf(payload), ThreadPoolConfigVersionRecord.class));
                }
            }
            return history;
        } catch (Exception exception) {
            log.warn("Failed to load thread pool config history for pool {}", poolName, exception);
            return Collections.emptyList();
        }
    }

    private void trimHistory(String poolName, String timelineKey, String versionsKey) {
        Long size = stringRedisTemplate.opsForZSet().zCard(timelineKey);
        if (size == null || size.longValue() <= properties.getConfigVersionHistorySize()) {
            return;
        }
        long removeCount = size.longValue() - properties.getConfigVersionHistorySize();
        Set<String> obsolete = stringRedisTemplate.opsForZSet().range(timelineKey, 0, removeCount - 1);
        if (obsolete == null || obsolete.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForZSet().removeRange(timelineKey, 0, removeCount - 1);
        stringRedisTemplate.opsForHash().delete(versionsKey, obsolete.toArray(new Object[0]));
        log.debug("Trimmed {} config history records for pool {}", removeCount, poolName);
    }

    private String latestKey() {
        return properties.getRedisConfigKey() + ":latest";
    }

    private String versionsKey() {
        return properties.getRedisConfigKey() + ":versions";
    }

    private String timelineKey(String poolName) {
        return properties.getRedisConfigKey() + ":timeline:" + poolName;
    }

    private String versionField(String poolName, Long version) {
        return poolName + ":" + version;
    }
}
