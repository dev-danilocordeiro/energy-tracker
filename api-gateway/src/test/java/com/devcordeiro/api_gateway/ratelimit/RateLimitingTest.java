package com.devcordeiro.api_gateway.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitingTest {

    private RateLimiter rateLimiter;
    private SimpleMeterRegistry meterRegistry;
    private AtomicInteger downstreamCalls;
    private HandlerFunction<ServerResponse> downstream;
    private MockHttpServletResponse servletResponse;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimiter.class);
        meterRegistry = new SimpleMeterRegistry();
        downstreamCalls = new AtomicInteger();
        downstream = request -> {
            downstreamCalls.incrementAndGet();
            // Built locally like the circuit breaker fallbacks, so its headers are read-only
            return ServerResponse.ok().build();
        };
        servletResponse = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aRequestWithinTheLimitReachesTheServiceAndReportsWhatIsLeft() throws Exception {
        when(rateLimiter.tryConsume("default", "client-a")).thenReturn(new RateLimiter.Decision(true, 100, 99, Duration.ZERO));

        ServerResponse response = rateLimiting(true).policy("default").filter(request("client-a"), downstream);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downstreamCalls).hasValue(1);
        assertThat(servletResponse.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(servletResponse.getHeader("X-RateLimit-Remaining")).isEqualTo("99");
    }

    @Test
    void aRequestOverTheLimitGets429WithAProblemDetailAndNeverReachesTheService() throws Exception {
        when(rateLimiter.tryConsume("default", "client-a"))
                .thenReturn(new RateLimiter.Decision(false, 100, 0, Duration.ofMillis(1200)));

        ServerResponse response = rateLimiting(true).policy("default").filter(request("client-a"), downstream);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(downstreamCalls).hasValue(0);
        assertThat(response.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.headers().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        ProblemDetail problem = problem(response);
        assertThat(problem.getType()).hasToString("https://energy-tracker.devcordeiro.com/problems/rate-limited");
        assertThat(problem.getInstance()).hasToString("/api/v1/user/1");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void retryAfterIsRoundedUpToWholeSecondsAndNeverZero() throws Exception {
        when(rateLimiter.tryConsume("default", "client-a"))
                .thenReturn(new RateLimiter.Decision(false, 100, 0, Duration.ofMillis(1200)))
                .thenReturn(new RateLimiter.Decision(false, 100, 0, Duration.ofMillis(20)));
        RateLimiting rateLimiting = rateLimiting(true);

        ServerResponse slow = rateLimiting.policy("default").filter(request("client-a"), downstream);
        ServerResponse fast = rateLimiting.policy("default").filter(request("client-a"), downstream);

        assertThat(slow.headers().getFirst("Retry-After")).isEqualTo("2");
        assertThat(fast.headers().getFirst("Retry-After")).isEqualTo("1");
    }

    @Test
    void eachCallerIsLimitedByTheSubjectOfTheirToken() throws Exception {
        when(rateLimiter.tryConsume(anyString(), anyString())).thenReturn(new RateLimiter.Decision(true, 10, 9, Duration.ZERO));
        RateLimiting rateLimiting = rateLimiting(true);

        rateLimiting.policy("insight").filter(request("service-account-1"), downstream);

        verify(rateLimiter).tryConsume("insight", "service-account-1");
    }

    @Test
    void whenRedisIsDownRequestsGoThroughUnlimitedInsteadOfFailing() throws Exception {
        when(rateLimiter.tryConsume("default", "client-a")).thenThrow(new IllegalStateException("redis down"));

        ServerResponse response = rateLimiting(true).policy("default").filter(request("client-a"), downstream);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downstreamCalls).hasValue(1);
        assertThat(meterRegistry.counter("gateway.rate.limit.requests", "policy", "default", "result", "error").count()).isEqualTo(1);
    }

    @Test
    void decisionsAreCountedPerPolicyAndResult() throws Exception {
        when(rateLimiter.tryConsume("default", "client-a"))
                .thenReturn(new RateLimiter.Decision(true, 100, 99, Duration.ZERO))
                .thenReturn(new RateLimiter.Decision(false, 100, 0, Duration.ofSeconds(1)));
        RateLimiting rateLimiting = rateLimiting(true);

        rateLimiting.policy("default").filter(request("client-a"), downstream);
        rateLimiting.policy("default").filter(request("client-a"), downstream);

        assertThat(meterRegistry.counter("gateway.rate.limit.requests", "policy", "default", "result", "allowed").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("gateway.rate.limit.requests", "policy", "default", "result", "rejected").count()).isEqualTo(1);
    }

    @Test
    void whenDisabledTheLimiterIsNotConsulted() throws Exception {
        ServerResponse response = rateLimiting(false).policy("default").filter(request("client-a"), downstream);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(rateLimiter);
    }

    private RateLimiting rateLimiting(boolean enabled) {
        RateLimitProperties properties = new RateLimitProperties(enabled, null,
                Map.of("default", new RateLimitProperties.Policy(100, 50, Duration.ofSeconds(1))));
        return new RateLimiting(rateLimiter, meterRegistry, properties);
    }

    private ServerRequest request(String subject) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/v1/user/1");
        servletRequest.setUserPrincipal(() -> subject);
        // What DispatcherServlet sets up for every request
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest, servletResponse));
        return ServerRequest.create(servletRequest, List.of());
    }

    @SuppressWarnings("unchecked")
    private static ProblemDetail problem(ServerResponse response) {
        return ((EntityResponse<ProblemDetail>) response).entity();
    }
}
