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
public class IngestionServiceRoutes {

    @Bean
    public RouterFunction<ServerResponse> ingestionRoute(RateLimiting rateLimiting) {
        return route("ingestion-service")
                .route(RequestPredicates.path("/api/v1/ingestion/**"), http())
                .before(uri("http://localhost:8083"))
                // Before the circuit breaker, so rejected requests never count as service failures
                .filter(rateLimiting.policy("default"))
                .filter(CircuitBreakerFilterFunctions.circuitBreaker(
                        "ingestionServiceCircuitBreaker",
                        URI.create("forward:/fallback/ingestion")
                ))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> ingestionFallbackRoute() {
        return route("ingestionFallbackRoute")
                .route(RequestPredicates.path("/fallback/ingestion"),
                        request -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("Ingestion service is down"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> ingestionServiceApiDocsRoute() {
        return route("ingestion-service-api-docs")
                .route(RequestPredicates.path("/aggregate/ingestion-service/v3/api-docs"), http())
                .before(uri("http://localhost:8083"))
                .before(setPath("/v3/api-docs"))
                .build();
    }
}
