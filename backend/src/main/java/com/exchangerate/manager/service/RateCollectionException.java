package com.exchangerate.manager.service;

/** Thrown when collecting and validating exchange rates fails. */
public class RateCollectionException extends RuntimeException {

    public RateCollectionException(String message) {
        super(message);
    }

    public RateCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
