package com.devcordeiro.api_gateway.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Route filter that spends one token per request from the caller's bucket.
 * <p>
 * The caller is the JWT subject: the service account for client_credentials tokens, the
 * user once end users log in. Requests without a principal never get here, because the
 * security chain rejects them first.
 * <p>
 * If Redis can't be reached the request goes through unlimited. Losing the limiter for a
 * while is better than losing the gateway; the circuit breakers still protect the services.
 */
@Component
public class RateLimiting {

    private static final Logger log = LoggerFactory.getLogger(RateLimiting.class);

    static final String PROBLEM_TYPE = "https://energy-tracker.devcordeiro.com/problems/rate-limited";
    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    // During a Redis outage every request fails over; one warning per interval is enough
    private static final Duration FAILURE_LOG_INTERVAL = Duration.ofSeconds(30);

    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final AtomicReference<Instant> lastFailureLog = new AtomicReference<>(Instant.MIN);

    public RateLimiting(RateLimiter rateLimiter, MeterRegistry meterRegistry, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        this.enabled = properties.enabled();
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> policy(String policy) {
        return (request, next) -> {
            if (!enabled) {
                return next.handle(request);
            }

            RateLimiter.Decision decision;
            try {
                decision = rateLimiter.tryConsume(policy, clientKey(request));
            } catch (RuntimeException e) {
                count(policy, "error");
                logFailure(policy, e);
                return next.handle(request);
            }

            if (!decision.allowed()) {
                count(policy, "rejected");
                return tooManyRequests(request, decision);
            }

            count(policy, "allowed");
            addLimitHeaders(decision);
            return next.handle(request);
        };
    }

    // Written to the servlet response up front, not to the ServerResponse afterwards: the
    // headers of locally built responses (the circuit breaker fallbacks) are read-only
    private static void addLimitHeaders(RateLimiter.Decision decision) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getResponse() != null) {
            attributes.getResponse().setHeader(LIMIT_HEADER, String.valueOf(decision.limit()));
            attributes.getResponse().setHeader(REMAINING_HEADER, String.valueOf(decision.remaining()));
        }
    }

    private static String clientKey(ServerRequest request) {
        return request.principal()
                .map(Principal::getName)
                .orElseGet(() -> "ip:" + request.remoteAddress().map(a -> a.getAddress().getHostAddress()).orElse("unknown"));
    }

    private static ServerResponse tooManyRequests(ServerRequest request, RateLimiter.Decision decision) {
        // Rounded up: "retry in 0 seconds" would invite an immediate retry that fails again
        long retryAfterSeconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Retry in " + retryAfterSeconds + " s.");
        problem.setTitle("Too Many Requests");
        problem.setType(URI.create(PROBLEM_TYPE));
        problem.setInstance(URI.create(request.path()));
        problem.setProperty("timestamp", Instant.now());

        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .header(LIMIT_HEADER, String.valueOf(decision.limit()))
                .header(REMAINING_HEADER, "0")
                .body(problem);
    }

    private void count(String policy, String result) {
        meterRegistry.counter("gateway.rate.limit.requests", "policy", policy, "result", result).increment();
    }

    private void logFailure(String policy, RuntimeException e) {
        Instant now = Instant.now();
        Instant last = lastFailureLog.get();
        if (now.isAfter(last.plus(FAILURE_LOG_INTERVAL)) && lastFailureLog.compareAndSet(last, now)) {
            log.warn("Rate limiter unavailable, letting {} requests through unlimited: {}", policy, e.getMessage());
        }
    }
}
