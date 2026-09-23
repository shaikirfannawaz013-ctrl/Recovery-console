package com.prp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        Jwt jwt,
        Cors cors,
        Ml ml,
        Topics topics,
        Retry retry,
        Fraud fraud,
        Duplicates duplicates,
        Demo demo) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Ml(String baseUrl, Duration timeout) {}
    public record Topics(String transactions, String recoveryEvents) {}
    public record Retry(long pollIntervalMs, int batchSize) {}
    public record Fraud(double anomalyThreshold, int velocityLimit) {}
    public record Duplicates(Duration window) {}
    public record Demo(boolean seedHistory, boolean traffic, long trafficIntervalMs) {}
}
