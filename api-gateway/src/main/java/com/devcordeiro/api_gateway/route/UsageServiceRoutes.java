package com.devcordeiro.api_gateway.route;

import com.devcordeiro.api_gateway.ratelimit.RateLimiting;
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class UsageServiceRoutes {

    @Bean
    public RouterFunction<ServerResponse> usageRoute(RateLimiting rateLimiting) {
        return route("usage-service")
                .route(RequestPredicates.path("/api/v1/usage/**"), http())
                .before(uri("http://localhost:8084"))
                // Before the circuit breaker, so rejected requests never count as service failures
                .filter(rateLimiting.policy("default"))
                .filter(CircuitBreakerFilterFunctions.circuitBreaker(
                        "usageServiceCircuitBreaker",
                        URI.create("forward:/fallback/usage")
                ))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> usageFallbackRoute() {
        return route("usageFallbackRoute")
                .route(RequestPredicates.path("/fallback/usage"),
                        request -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("Usage service is down"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> usageServiceApiDocsRoute() {
        return route("usage-service-api-docs")
                .route(RequestPredicates.path("/aggregate/usage-service/v3/api-docs"), http())
                .before(uri("http://localhost:8084"))
                .before(setPath("/v3/api-docs"))
                .build();
    }
}
