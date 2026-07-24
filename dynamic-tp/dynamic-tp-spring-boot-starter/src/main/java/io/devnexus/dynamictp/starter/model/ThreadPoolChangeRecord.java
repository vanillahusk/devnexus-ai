package io.devnexus.dynamictp.starter.model;

public class ThreadPoolChangeRecord {

    private final String poolName;
    private final String requestId;
    private final Long version;
    private final String source;
    private final String reason;
    private final String status;
    private final String message;
    private final long timestamp;
    private final ThreadPoolConfig before;
    private final ThreadPoolConfig after;

    public ThreadPoolChangeRecord(String poolName, String requestId, Long version, String source, String reason,
                                  String status, String message, long timestamp,
                                  ThreadPoolConfig before, ThreadPoolConfig after) {
        this.poolName = poolName;
        this.requestId = requestId;
        this.version = version;
        this.source = source;
        this.reason = reason;
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
        this.before = before;
        this.after = after;
    }

    public String getPoolName() {
        return poolName;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getVersion() {
        return version;
    }

    public String getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ThreadPoolConfig getBefore() {
        return before;
    }

    public ThreadPoolConfig getAfter() {
        return after;
    }
}