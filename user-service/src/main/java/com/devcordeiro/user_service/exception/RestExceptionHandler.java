package com.devcordeiro.user_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "User not found", ex.getMessage(), "user-not-found", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        // A mensagem original carrega nome de constraint e SQL, entao fica no log e nao na resposta.
        log.warn("Constraint violation while writing a user", ex);
        return problem(HttpStatus.CONFLICT, "User conflicts with existing data",
                "The user could not be saved because it breaks a database constraint. "
                        + "The e-mail may already be in use, or a required field may be missing.",
                "user-conflict", request);
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
