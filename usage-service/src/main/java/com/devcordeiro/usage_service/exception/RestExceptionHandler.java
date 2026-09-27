package com.devcordeiro.usage_service.exception;

import com.influxdb.exceptions.InfluxException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {

    private static final String PROBLEM_BASE = "https://energy-tracker.devcordeiro.com/problems/";

    @ExceptionHandler(InfluxException.class)
    public ProblemDetail handleInfluxFailure(InfluxException ex, HttpServletRequest request) {
        log.error("InfluxDB query failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Usage data unavailable",
                "Energy usage could not be read from InfluxDB. Try again later.",
                "usage-data-unavailable", request);
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail handleDeviceServiceFailure(RestClientException ex, HttpServletRequest request) {
        log.error("device-service call failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Device data unavailable",
                "The user's devices could not be fetched from device-service. Try again later.",
                "device-data-unavailable", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_BASE + type));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
