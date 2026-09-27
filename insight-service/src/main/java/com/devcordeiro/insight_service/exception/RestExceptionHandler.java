package com.devcordeiro.insight_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {

    private static final String PROBLEM_BASE = "https://energy-tracker.devcordeiro.com/problems/";

    // Ollama is also called through RestClient, so only the wrapped usage-service failure is matched here
    @ExceptionHandler(UsageServiceUnavailableException.class)
    public ProblemDetail handleUsageServiceFailure(UsageServiceUnavailableException ex, HttpServletRequest request) {
        log.error("usage-service call failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Usage data unavailable",
                "The user's energy usage could not be fetched from usage-service. Try again later.",
                "usage-data-unavailable", request);
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
