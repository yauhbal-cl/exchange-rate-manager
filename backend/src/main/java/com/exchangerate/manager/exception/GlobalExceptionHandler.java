package com.exchangerate.manager.exception;

import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.service.CollectionInProgressException;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central mapping of application exceptions to {@link ProblemDetail} responses. Controllers and
 * services let these exceptions propagate rather than catching them locally.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FixerApiException.class)
    public ProblemDetail handleFixerApiException(FixerApiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(CollectionInProgressException.class)
    public ProblemDetail handleCollectionInProgress(CollectionInProgressException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UnknownCurrencyException.class)
    public ProblemDetail handleUnknownCurrency(UnknownCurrencyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SameCurrencyException.class)
    public ProblemDetail handleSameCurrency(SameCurrencyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handleInvalidDateRange(InvalidDateRangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TrendRangeTooLargeException.class)
    public ProblemDetail handleTrendRangeTooLarge(TrendRangeTooLargeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RateDataNotFoundException.class)
    public ProblemDetail handleRateDataNotFound(RateDataNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AiInsightUnavailableException.class)
    public ProblemDetail handleAiInsightUnavailable(AiInsightUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
