package io.devnexus.dynamictp.starter.model;

public class ThreadPoolSnapshot {

    private final String poolName;
    private final int poolSize;
    private final int coreSize;
    private final int maxSize;
    private final int activeCount;
    private final int queueSize;
    private final int queueCapacity;
    private final long completedTaskCount;
    private final long taskCount;
    private final int largestPoolSize;
    private final long keepAliveSeconds;
    private final boolean queueResizable;

    public ThreadPoolSnapshot(String poolName, int poolSize, int coreSize, int maxSize, int activeCount,
                              int queueSize, int queueCapacity, long completedTaskCount, long taskCount,
                              int largestPoolSize, long keepAliveSeconds, boolean queueResizable) {
        this.poolName = poolName;
        this.poolSize = poolSize;
        this.coreSize = coreSize;
        this.maxSize = maxSize;
        this.activeCount = activeCount;
        this.queueSize = queueSize;
        this.queueCapacity = queueCapacity;
        this.completedTaskCount = completedTaskCount;
        this.taskCount = taskCount;
        this.largestPoolSize = largestPoolSize;
        this.keepAliveSeconds = keepAliveSeconds;
        this.queueResizable = queueResizable;
    }

    public String getPoolName() {
        return poolName;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getCoreSize() {
        return coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public long getTaskCount() {
        return taskCount;
    }

    public int getLargestPoolSize() {
        return largestPoolSize;
    }

    public long getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public boolean isQueueResizable() {
        return queueResizable;
    }

    public double getQueueUsage() {
        if (queueCapacity <= 0) {
            return 0D;
        }
        return (double) queueSize / (double) queueCapacity;
    }

    public double getActiveUsage() {
        if (maxSize <= 0) {
            return 0D;
        }
        return (double) activeCount / (double) maxSize;
    }
}