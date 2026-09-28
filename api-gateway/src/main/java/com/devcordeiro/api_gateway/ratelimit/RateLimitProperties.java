package com.devcordeiro.api_gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Token buckets per client. A policy allows bursts of up to {@code capacity} requests and
 * refills {@code refillTokens} every {@code refillPeriod}, so its sustained rate is
 * refillTokens / refillPeriod.
 */
@ConfigurationProperties("rate-limit")
public record RateLimitProperties(boolean enabled, Redis redis, Map<String, Policy> policies) {

    public record Redis(String host, int port, Duration timeout) {
    }

    public record Policy(long capacity, long refillTokens, Duration refillPeriod) {
    }

    public Policy policy(String name) {
        Policy policy = policies.get(name);
        if (policy == null) {
            throw new IllegalArgumentException("No rate limit policy named " + name);
        }
        return policy;
    }
}
