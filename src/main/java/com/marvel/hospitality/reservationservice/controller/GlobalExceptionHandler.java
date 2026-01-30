
package com.marvel.hospitality.reservationservice.controller;

import com.marvel.hospitality.reservationservice.exception.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value",
                        (e1, e2) -> e1, LinkedHashMap::new));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed for request body"
        );
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());

        log.warn("Validation error on {}: {}", request.getDescription(false), errors);
        return problem;
    }


    @ExceptionHandler({ReservationValidationException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex, WebRequest request) {
        return buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                null,
                request
        );
    }

    @ExceptionHandler(PaymentRejectedException.class)
    public ProblemDetail handlePaymentRejected(PaymentRejectedException ex, WebRequest request) {
        return buildProblemDetail(
                HttpStatus.PAYMENT_REQUIRED,
                ex.getMessage(),
                null,
                request
        );
    }

    @ExceptionHandler(CreditCardServiceUnavailableException.class)
    public ProblemDetail handleCreditCardUnavailable(CreditCardServiceUnavailableException ex,
                                                     WebRequest request) {
        return buildProblemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Credit card payment service call failed",
                Map.of("retryAfter", "30s", "cause", ex.getCause()),
                request
        );
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleCircuitOpen(CallNotPermittedException ex, WebRequest request) {
        return buildProblemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Circuit breaker is open - credit card service temporarily unavailable",
                Map.of("retryAfter", "60s"),
                request
        );
    }


    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAllOtherExceptions(Exception ex, WebRequest request) {
        log.error("Unhandled exception occurred", ex);

        return buildProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                null,
                request
        );
    }


    private ProblemDetail buildProblemDetail(
            HttpStatus status,
            String detail,
            Map<String, ?> properties,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getDescription(false));

        if (properties != null && !properties.isEmpty()) {
            properties.forEach(problem::setProperty);
        }

        return problem;
    }
}
