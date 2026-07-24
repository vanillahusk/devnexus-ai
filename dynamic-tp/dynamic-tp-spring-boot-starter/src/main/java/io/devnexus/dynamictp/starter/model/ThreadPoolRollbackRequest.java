package io.devnexus.dynamictp.starter.model;

public class ThreadPoolRollbackRequest {

    private String requestId;

    private Long version;

    private String poolName;

    private Long targetVersion;

    private String source;

    private String reason;

    private Long timestamp;

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

    public Long getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(Long targetVersion) {
        this.targetVersion = targetVersion;
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

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public void validate() {
        if (poolName == null || poolName.trim().isEmpty()) {
            throw new IllegalArgumentException("poolName must not be blank");
        }
        if (targetVersion == null || targetVersion.longValue() < 0L) {
            throw new IllegalArgumentException("targetVersion must be >= 0");
        }
        if (version != null && version.longValue() < 0L) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        if (requestId != null && requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }
}