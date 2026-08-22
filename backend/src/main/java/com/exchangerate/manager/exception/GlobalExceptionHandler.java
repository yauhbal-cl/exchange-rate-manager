package com.exchangerate.manager.exception;

import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.service.CollectionInProgressException;

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
}
