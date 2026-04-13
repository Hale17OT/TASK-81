package com.campusstore.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "campusstore")
public class AppProperties {

    @JsonIgnore
    private String masterKeyPassphrase;
    private Encryption encryption = new Encryption();
    private RateLimit rateLimit = new RateLimit();
    private Retention retention = new Retention();
    private Crawler crawler = new Crawler();

    @JsonIgnore
    public String getMasterKeyPassphrase() { return masterKeyPassphrase; }
    public void setMasterKeyPassphrase(String masterKeyPassphrase) { this.masterKeyPassphrase = masterKeyPassphrase; }

    @Override
    public String toString() {
        return "AppProperties{encryption=..., rateLimit=..., retention=..., crawler=...}";
    }
    public Encryption getEncryption() { return encryption; }
    public void setEncryption(Encryption encryption) { this.encryption = encryption; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public Retention getRetention() { return retention; }
    public void setRetention(Retention retention) { this.retention = retention; }
    public Crawler getCrawler() { return crawler; }
    public void setCrawler(Crawler crawler) { this.crawler = crawler; }

    public static class Encryption {
        private String salt = "campusstore-aes-salt-2026";
        private int iterations = 310000;

        public String getSalt() { return salt; }
        public void setSalt(String salt) { this.salt = salt; }
        public int getIterations() { return iterations; }
        public void setIterations(int iterations) { this.iterations = iterations; }
    }

    public static class RateLimit {
        private int anonymousRequestsPerMinute = 30;
        private int authenticatedRequestsPerMinute = 120;
        private int lockoutThreshold = 10;
        private int lockoutDurationMinutes = 5;

        public int getAnonymousRequestsPerMinute() { return anonymousRequestsPerMinute; }
        public void setAnonymousRequestsPerMinute(int v) { this.anonymousRequestsPerMinute = v; }
        public int getAuthenticatedRequestsPerMinute() { return authenticatedRequestsPerMinute; }
        public void setAuthenticatedRequestsPerMinute(int v) { this.authenticatedRequestsPerMinute = v; }
        public int getLockoutThreshold() { return lockoutThreshold; }
        public void setLockoutThreshold(int v) { this.lockoutThreshold = v; }
        public int getLockoutDurationMinutes() { return lockoutDurationMinutes; }
        public void setLockoutDurationMinutes(int v) { this.lockoutDurationMinutes = v; }
    }

    public static class Retention {
        private int operationalDays = 365;
        private int auditDays = 2555;
        private int userDeletionDays = 30;

        public int getOperationalDays() { return operationalDays; }
        public void setOperationalDays(int v) { this.operationalDays = v; }
        public int getAuditDays() { return auditDays; }
        public void setAuditDays(int v) { this.auditDays = v; }
        public int getUserDeletionDays() { return userDeletionDays; }
        public void setUserDeletionDays(int v) { this.userDeletionDays = v; }
    }

    public static class Crawler {
        private int snapshotCap = 50;
        private double successRateThreshold = 95.0;
        private int latencyP95ThresholdMs = 2000;

        public int getSnapshotCap() { return snapshotCap; }
        public void setSnapshotCap(int v) { this.snapshotCap = v; }
        public double getSuccessRateThreshold() { return successRateThreshold; }
        public void setSuccessRateThreshold(double v) { this.successRateThreshold = v; }
        public int getLatencyP95ThresholdMs() { return latencyP95ThresholdMs; }
        public void setLatencyP95ThresholdMs(int v) { this.latencyP95ThresholdMs = v; }
    }
}
