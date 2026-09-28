package com.devcordeiro.api_gateway.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs against the Redis from docker compose, like the contextLoads tests. Skipped when
 * it is not running.
 */
class RedisRateLimiterTest {

    private static final RateLimitProperties.Redis REDIS = new RateLimitProperties.Redis("localhost", 6379, Duration.ofMillis(500));
    // Refills too slowly to matter during a test, so every token spent stays spent
    private static final RateLimitProperties.Policy FIVE_PER_HOUR = new RateLimitProperties.Policy(5, 5, Duration.ofHours(1));

    private RedisRateLimiter rateLimiter;
    private String client;

    @BeforeEach
    void setUp() {
        assumeTrue(redisIsUp(), "Redis is not running on localhost:6379 (docker compose up -d redis)");
        rateLimiter = new RedisRateLimiter(new RateLimitProperties(true, REDIS, Map.of("test", FIVE_PER_HOUR)));
        client = "test-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (rateLimiter == null) {
            return;
        }
        rateLimiter.close();
        // The test policy refills over an hour, so its keys would otherwise linger that long
        RedisClient redis = RedisClient.create("redis://" + REDIS.host() + ":" + REDIS.port());
        try (StatefulRedisConnection<String, String> connection = redis.connect()) {
            List<String> keys = connection.sync().keys(RedisRateLimiter.KEY_PREFIX + "test:*" + client + "*");
            if (!keys.isEmpty()) {
                connection.sync().del(keys.toArray(String[]::new));
            }
        } finally {
            redis.shutdown();
        }
    }

    @Test
    void aClientCanSpendTheWholeBurstAndIsThenRejectedWithATimeToWait() {
        for (int i = 4; i >= 0; i--) {
            RateLimiter.Decision decision = rateLimiter.tryConsume("test", client);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(i);
        }

        RateLimiter.Decision rejected = rateLimiter.tryConsume("test", client);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.limit()).isEqualTo(5);
        assertThat(rejected.retryAfter()).isPositive();
    }

    @Test
    void clientsHaveSeparateBuckets() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryConsume("test", client);
        }

        assertThat(rateLimiter.tryConsume("test", client + "-other").allowed()).isTrue();
    }

    @Test
    void concurrentRequestsNeverSpendMoreTokensThanTheBucketHolds() throws Exception {
        int threads = 20;
        int requestsPerThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> allowedPerThread = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                allowedPerThread.add(pool.submit(() -> {
                    start.await();
                    int allowed = 0;
                    for (int r = 0; r < requestsPerThread; r++) {
                        if (rateLimiter.tryConsume("test", client).allowed()) {
                            allowed++;
                        }
                    }
                    return allowed;
                }));
            }
            start.countDown();

            int allowed = 0;
            for (Future<Integer> future : allowedPerThread) {
                allowed += future.get(10, TimeUnit.SECONDS);
            }
            // 100 requests race for 5 tokens
            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aClientThatCannotReachRedisGetsAnErrorInsteadOfHanging() {
        RedisRateLimiter unreachable = new RedisRateLimiter(new RateLimitProperties(true,
                new RateLimitProperties.Redis("localhost", 1, Duration.ofMillis(200)), Map.of("test", FIVE_PER_HOUR)));
        try {
            assertThatThrownBy(() -> unreachable.tryConsume("test", client)).isInstanceOf(RuntimeException.class);
            // Within the backoff the next call fails at once, without another connection attempt
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> unreachable.tryConsume("test", client)).isInstanceOf(IllegalStateException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(50));
        } finally {
            unreachable.close();
        }
    }

    private static boolean redisIsUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REDIS.host(), REDIS.port()), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
