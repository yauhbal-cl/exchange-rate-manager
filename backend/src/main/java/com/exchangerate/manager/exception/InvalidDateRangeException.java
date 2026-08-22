package com.exchangerate.manager.exception;

/**
 * Thrown when a requested trend date range has a {@code startDate} after its {@code endDate}.
 */
public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(String message) {
        super(message);
    }
}
