package io.devnexus.dynamictp.starter.model;

public class ThreadPoolConfig {

    private final int coreSize;
    private final int maxSize;
    private final int queueCapacity;
    private final long keepAliveSeconds;

    public ThreadPoolConfig(int coreSize, int maxSize, int queueCapacity, long keepAliveSeconds) {
        this.coreSize = coreSize;
        this.maxSize = maxSize;
        this.queueCapacity = queueCapacity;
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getCoreSize() {
        return coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public long getKeepAliveSeconds() {
        return keepAliveSeconds;
    }
}