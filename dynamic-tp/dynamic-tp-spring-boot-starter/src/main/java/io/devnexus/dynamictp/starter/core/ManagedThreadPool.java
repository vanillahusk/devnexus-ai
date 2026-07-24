package io.devnexus.dynamictp.starter.core;

import io.devnexus.dynamictp.starter.model.ThreadPoolChangeRecord;
import io.devnexus.dynamictp.starter.model.ThreadPoolConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

public class ManagedThreadPool {

    private final String poolName;
    private final ThreadPoolExecutor executor;
    private final boolean queueResizable;
    private final ThreadPoolConfig initialConfig;
    private final ReentrantLock updateLock = new ReentrantLock();
    private final Deque<ThreadPoolChangeRecord> changeHistory = new ArrayDeque<ThreadPoolChangeRecord>();

    private volatile ThreadPoolConfig currentConfig;
    private volatile Long latestVersion;
    private volatile String latestRequestId;
    private volatile long latestUpdatedAt;

    public ManagedThreadPool(String poolName, ThreadPoolExecutor executor, ThreadPoolConfig initialConfig,
                             boolean queueResizable) {
        this.poolName = poolName;
        this.executor = executor;
        this.initialConfig = initialConfig;
        this.currentConfig = initialConfig;
        this.queueResizable = queueResizable;
    }

    public String getPoolName() {
        return poolName;
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public ThreadPoolConfig getInitialConfig() {
        return initialConfig;
    }

    public ThreadPoolConfig getCurrentConfig() {
        return currentConfig;
    }

    public boolean isQueueResizable() {
        return queueResizable;
    }

    public Long getLatestVersion() {
        return latestVersion;
    }

    public String getLatestRequestId() {
        return latestRequestId;
    }

    public long getLatestUpdatedAt() {
        return latestUpdatedAt;
    }

    public ReentrantLock getUpdateLock() {
        return updateLock;
    }

    public void markApplied(ThreadPoolConfig config, Long version, String requestId, long updatedAt) {
        this.currentConfig = config;
        this.latestVersion = version;
        this.latestRequestId = requestId;
        this.latestUpdatedAt = updatedAt;
    }

    public synchronized void record(ThreadPoolChangeRecord record, int historyLimit) {
        changeHistory.addFirst(record);
        while (changeHistory.size() > historyLimit) {
            changeHistory.removeLast();
        }
    }

    public synchronized List<ThreadPoolChangeRecord> history(int limit) {
        List<ThreadPoolChangeRecord> records = new ArrayList<ThreadPoolChangeRecord>();
        int count = 0;
        for (ThreadPoolChangeRecord record : changeHistory) {
            if (count >= limit) {
                break;
            }
            records.add(record);
            count++;
        }
        return records;
    }
}