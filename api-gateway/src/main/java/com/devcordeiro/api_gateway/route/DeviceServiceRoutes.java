package com.devcordeiro.api_gateway.route;

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
public class DeviceServiceRoutes {

    @Bean
    public RouterFunction<ServerResponse> deviceRoute() {
        return route("device-service")
                .route(RequestPredicates.path("/api/v1/device/**"), http())
                .before(uri("http://localhost:8082"))
                .filter(CircuitBreakerFilterFunctions.circuitBreaker(
                        "deviceServiceCircuitBreaker",
                        URI.create("forward:/fallback/device")
                ))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> deviceFallbackRoute() {
        return route("deviceFallbackRoute")
                .route(RequestPredicates.path("/fallback/device"),
                        request -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("Device service is down"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> deviceServiceApiDocsRoute() {
        return route("device-service-api-docs")
                .route(RequestPredicates.path("/aggregate/device-service/v3/api-docs"), http())
                .before(uri("http://localhost:8082"))
                .before(setPath("/v3/api-docs"))
                .build();
    }
}
