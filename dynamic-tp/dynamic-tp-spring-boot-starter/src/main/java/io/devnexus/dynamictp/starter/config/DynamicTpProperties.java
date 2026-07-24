package io.devnexus.dynamictp.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dynamic.tp")
public class DynamicTpProperties {

    private boolean enabled = true;

    private String redisChannel = "dynamic-tp:refresh";

    private long monitorIntervalMs = 5000L;

    private double alertThreshold = 0.85D;

    private long alertCooldownMs = 60000L;

    private double criticalThreshold = 0.95D;

    private int changeHistorySize = 20;

    private int endpointHistorySize = 10;

    private boolean rejectStaleVersion = true;

    private boolean allowQueueCapacityShrink = false;

    private boolean monitorLogEnabled = true;

    private boolean metricsEnabled = true;

    private boolean configCacheEnabled = true;

    private boolean syncConfigOnStartup = true;

    private String redisConfigKey = "dynamic-tp:configs";

    private int configVersionHistorySize = 50;

    /** Safety guardrails against an accidentally oversized online configuration. */
    private int maxCoreSize = 128;

    private int maxPoolSize = 256;

    private int maxQueueCapacity = 100000;

    private String dingTalkWebhook;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedisChannel() {
        return redisChannel;
    }

    public void setRedisChannel(String redisChannel) {
        this.redisChannel = redisChannel;
    }

    public long getMonitorIntervalMs() {
        return monitorIntervalMs;
    }

    public void setMonitorIntervalMs(long monitorIntervalMs) {
        this.monitorIntervalMs = monitorIntervalMs;
    }

    public double getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(double alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    public long getAlertCooldownMs() {
        return alertCooldownMs;
    }

    public void setAlertCooldownMs(long alertCooldownMs) {
        this.alertCooldownMs = alertCooldownMs;
    }

    public double getCriticalThreshold() {
        return criticalThreshold;
    }

    public void setCriticalThreshold(double criticalThreshold) {
        this.criticalThreshold = criticalThreshold;
    }

    public int getChangeHistorySize() {
        return changeHistorySize;
    }

    public void setChangeHistorySize(int changeHistorySize) {
        this.changeHistorySize = changeHistorySize;
    }

    public int getEndpointHistorySize() {
        return endpointHistorySize;
    }

    public void setEndpointHistorySize(int endpointHistorySize) {
        this.endpointHistorySize = endpointHistorySize;
    }

    public boolean isRejectStaleVersion() {
        return rejectStaleVersion;
    }

    public void setRejectStaleVersion(boolean rejectStaleVersion) {
        this.rejectStaleVersion = rejectStaleVersion;
    }

    public boolean isAllowQueueCapacityShrink() {
        return allowQueueCapacityShrink;
    }

    public void setAllowQueueCapacityShrink(boolean allowQueueCapacityShrink) {
        this.allowQueueCapacityShrink = allowQueueCapacityShrink;
    }

    public boolean isMonitorLogEnabled() {
        return monitorLogEnabled;
    }

    public void setMonitorLogEnabled(boolean monitorLogEnabled) {
        this.monitorLogEnabled = monitorLogEnabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public boolean isConfigCacheEnabled() {
        return configCacheEnabled;
    }

    public void setConfigCacheEnabled(boolean configCacheEnabled) {
        this.configCacheEnabled = configCacheEnabled;
    }

    public boolean isSyncConfigOnStartup() {
        return syncConfigOnStartup;
    }

    public void setSyncConfigOnStartup(boolean syncConfigOnStartup) {
        this.syncConfigOnStartup = syncConfigOnStartup;
    }

    public String getRedisConfigKey() {
        return redisConfigKey;
    }

    public void setRedisConfigKey(String redisConfigKey) {
        this.redisConfigKey = redisConfigKey;
    }

    public int getConfigVersionHistorySize() {
        return configVersionHistorySize;
    }

    public void setConfigVersionHistorySize(int configVersionHistorySize) {
        this.configVersionHistorySize = configVersionHistorySize;
    }

    public int getMaxCoreSize() {
        return maxCoreSize;
    }

    public void setMaxCoreSize(int maxCoreSize) {
        this.maxCoreSize = maxCoreSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getMaxQueueCapacity() {
        return maxQueueCapacity;
    }

    public void setMaxQueueCapacity(int maxQueueCapacity) {
        this.maxQueueCapacity = maxQueueCapacity;
    }

    public String getDingTalkWebhook() {
        return dingTalkWebhook;
    }

    public void setDingTalkWebhook(String dingTalkWebhook) {
        this.dingTalkWebhook = dingTalkWebhook;
    }
}
