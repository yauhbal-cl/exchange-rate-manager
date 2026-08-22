package com.exchangerate.manager.exception;

/**
 * Thrown when a requested date range spans more daily observations than the AI insight
 * generation path can summarize (~365 daily points), rejected before querying the database or
 * calling the AI model.
 */
public class TrendRangeTooLargeException extends RuntimeException {

    public TrendRangeTooLargeException(String message) {
        super(message);
    }
}
