package com.exchangerate.manager.exception;

/**
 * Thrown when no rate data exists for the resolved or requested date, as determined by
 * {@code ExchangeRateService.lookup}.
 */
public class RateDataNotFoundException extends RuntimeException {

    public RateDataNotFoundException(String message) {
        super(message);
    }
}
