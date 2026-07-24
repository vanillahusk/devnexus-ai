package io.devnexus.dynamictp.starter.repository;

import io.devnexus.dynamictp.starter.model.ThreadPoolConfigVersionRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NoopThreadPoolConfigRepository implements ThreadPoolConfigRepository {

    @Override
    public void save(ThreadPoolRefreshCommand command) {
    }

    @Override
    public ThreadPoolRefreshCommand find(String poolName) {
        return null;
    }

    @Override
    public Map<String, ThreadPoolRefreshCommand> findAll() {
        return Collections.emptyMap();
    }

    @Override
    public ThreadPoolConfigVersionRecord findVersion(String poolName, Long version) {
        return null;
    }

    @Override
    public List<ThreadPoolConfigVersionRecord> history(String poolName, int limit) {
        return Collections.emptyList();
    }
}