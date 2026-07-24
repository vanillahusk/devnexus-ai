package io.devnexus.dynamictp.starter.repository;

import io.devnexus.dynamictp.starter.model.ThreadPoolRefreshCommand;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfigVersionRecord;
import java.util.List;
import java.util.Map;

public interface ThreadPoolConfigRepository {

    void save(ThreadPoolRefreshCommand command);

    ThreadPoolRefreshCommand find(String poolName);

    Map<String, ThreadPoolRefreshCommand> findAll();

    ThreadPoolConfigVersionRecord findVersion(String poolName, Long version);

    List<ThreadPoolConfigVersionRecord> history(String poolName, int limit);
}