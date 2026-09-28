package com.devcordeiro.api_gateway.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Buckets live in Redis, so every gateway instance shares the same limits. Bucket4j
 * updates them with compare-and-swap, which keeps concurrent requests from overspending.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    static final String KEY_PREFIX = "rate-limit:";
    // After a failed connection attempt, requests skip Redis for this long instead of each
    // paying the connect timeout
    private static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(10);

    private final Map<String, BucketConfiguration> configurations;
    private final Map<String, String> keyPrefixes;
    private final RedisClient redisClient;

    private volatile ProxyManager<String> buckets;
    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile Instant nextConnectAttempt = Instant.MIN;

    public RedisRateLimiter(RateLimitProperties properties) {
        this.configurations = properties.policies().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> configuration(e.getValue())));
        // The policy's settings are part of the key: changing a limit starts fresh buckets
        // right away, and the old keys expire on their own
        this.keyPrefixes = properties.policies().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        e -> KEY_PREFIX + e.getKey() + ":" + Integer.toHexString(fingerprint(e.getValue())) + ":"));

        Duration timeout = properties.redis().timeout();
        this.redisClient = RedisClient.create(RedisURI.builder()
                .withHost(properties.redis().host())
                .withPort(properties.redis().port())
                .withTimeout(timeout)
                .build());
        this.redisClient.setOptions(ClientOptions.builder()
                .timeoutOptions(TimeoutOptions.enabled(timeout))
                // While disconnected, fail commands at once instead of queueing them until
                // Redis comes back
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }

    @Override
    public Decision tryConsume(String policy, String key) {
        BucketConfiguration configuration = configurations.get(policy);
        if (configuration == null) {
            throw new IllegalArgumentException("No rate limit policy named " + policy);
        }

        ConsumptionProbe probe = buckets().builder()
                .build(keyPrefixes.get(policy) + key, () -> configuration)
                .tryConsumeAndReturnRemaining(1);

        long capacity = configuration.getBandwidths()[0].getCapacity();
        return new Decision(probe.isConsumed(), capacity, probe.getRemainingTokens(),
                Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    // Connected on first use, not at startup, so the gateway starts and serves traffic
    // (unlimited) while Redis is down. Lettuce reconnects on its own once connected.
    private ProxyManager<String> buckets() {
        ProxyManager<String> current = buckets;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (buckets != null) {
                return buckets;
            }
            Instant now = Instant.now();
            if (now.isBefore(nextConnectAttempt)) {
                throw new IllegalStateException("Redis unavailable, next connection attempt at " + nextConnectAttempt);
            }
            try {
                connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                buckets = Bucket4jLettuce.casBasedBuilder(connection)
                        // A key is dropped once its bucket would be full again, so idle
                        // clients don't pile up in Redis
                        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                        .build();
                return buckets;
            } catch (RuntimeException e) {
                nextConnectAttempt = now.plus(RECONNECT_BACKOFF);
                throw e;
            }
        }
    }

    private static BucketConfiguration configuration(RateLimitProperties.Policy policy) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(policy.capacity())
                        .refillGreedy(policy.refillTokens(), policy.refillPeriod())
                        .build())
                .build();
    }

    private static int fingerprint(RateLimitProperties.Policy policy) {
        return Objects.hash(policy.capacity(), policy.refillTokens(), policy.refillPeriod());
    }

    @PreDestroy
    void close() {
        if (connection != null) {
            connection.close();
        }
        redisClient.shutdown();
    }
}
