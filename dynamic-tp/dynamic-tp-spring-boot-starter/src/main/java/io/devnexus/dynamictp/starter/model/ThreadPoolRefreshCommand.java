package io.devnexus.dynamictp.starter.model;

public class ThreadPoolRefreshCommand {

    private String requestId;

    private Long version;

    private String poolName;

    private String source;

    private String reason;

    private Integer coreSize;

    private Integer maxSize;

    private Integer queueCapacity;

    private Long keepAliveSeconds;

    private Long timestamp;

    private Boolean rollback;

    private Long rollbackFromVersion;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(Integer coreSize) {
        this.coreSize = coreSize;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public Integer getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(Integer queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Long getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(Long keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getRollback() {
        return rollback;
    }

    public void setRollback(Boolean rollback) {
        this.rollback = rollback;
    }

    public Long getRollbackFromVersion() {
        return rollbackFromVersion;
    }

    public void setRollbackFromVersion(Long rollbackFromVersion) {
        this.rollbackFromVersion = rollbackFromVersion;
    }

    public boolean hasChanges() {
        return coreSize != null || maxSize != null || queueCapacity != null || keepAliveSeconds != null;
    }

    public void validate() {
        if (poolName == null || poolName.trim().isEmpty()) {
            throw new IllegalArgumentException("poolName must not be blank");
        }
        if (requestId != null && requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (version != null && version < 0L) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        if (rollbackFromVersion != null && rollbackFromVersion < 0L) {
            throw new IllegalArgumentException("rollbackFromVersion must be >= 0");
        }
        if (coreSize != null && coreSize < 0) {
            throw new IllegalArgumentException("coreSize must be >= 0");
        }
        if (maxSize != null && maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        if (queueCapacity != null && queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }
        if (keepAliveSeconds != null && keepAliveSeconds < 0L) {
            throw new IllegalArgumentException("keepAliveSeconds must be >= 0");
        }
        if (coreSize != null && maxSize != null && coreSize > maxSize) {
            throw new IllegalArgumentException("coreSize cannot be greater than maxSize");
        }
        if (Boolean.TRUE.equals(rollback) && rollbackFromVersion == null) {
            throw new IllegalArgumentException("rollbackFromVersion must be provided when rollback=true");
        }
        if (!hasChanges()) {
            throw new IllegalArgumentException("At least one thread pool property must be provided");
        }
    }
}