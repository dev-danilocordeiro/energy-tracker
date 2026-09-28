package com.devcordeiro.api_gateway.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    /**
     * Takes one token from the bucket of {@code key} under {@code policy}.
     *
     * @throws RuntimeException when the bucket store can't be reached
     */
    Decision tryConsume(String policy, String key);

    record Decision(boolean allowed, long limit, long remaining, Duration retryAfter) {
    }
}
