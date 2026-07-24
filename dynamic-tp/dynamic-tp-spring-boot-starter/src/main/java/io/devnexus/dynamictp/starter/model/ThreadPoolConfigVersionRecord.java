package io.devnexus.dynamictp.starter.model;

public class ThreadPoolConfigVersionRecord {

    private String poolName;
    private Long version;
    private String requestId;
    private String source;
    private String reason;
    private Integer coreSize;
    private Integer maxSize;
    private Integer queueCapacity;
    private Long keepAliveSeconds;
    private Long timestamp;
    private Boolean rollback;
    private Long rollbackFromVersion;
    private String state;

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public ThreadPoolRefreshCommand toRefreshCommand() {
        ThreadPoolRefreshCommand command = new ThreadPoolRefreshCommand();
        command.setPoolName(poolName);
        command.setRequestId(requestId);
        command.setVersion(version);
        command.setSource(source);
        command.setReason(reason);
        command.setCoreSize(coreSize);
        command.setMaxSize(maxSize);
        command.setQueueCapacity(queueCapacity);
        command.setKeepAliveSeconds(keepAliveSeconds);
        command.setTimestamp(timestamp);
        command.setRollback(rollback);
        command.setRollbackFromVersion(rollbackFromVersion);
        return command;
    }

    public static ThreadPoolConfigVersionRecord fromCommand(ThreadPoolRefreshCommand command, String state) {
        ThreadPoolConfigVersionRecord record = new ThreadPoolConfigVersionRecord();
        record.setPoolName(command.getPoolName());
        record.setRequestId(command.getRequestId());
        record.setVersion(command.getVersion());
        record.setSource(command.getSource());
        record.setReason(command.getReason());
        record.setCoreSize(command.getCoreSize());
        record.setMaxSize(command.getMaxSize());
        record.setQueueCapacity(command.getQueueCapacity());
        record.setKeepAliveSeconds(command.getKeepAliveSeconds());
        record.setTimestamp(command.getTimestamp());
        record.setRollback(command.getRollback());
        record.setRollbackFromVersion(command.getRollbackFromVersion());
        record.setState(state);
        return record;
    }
}