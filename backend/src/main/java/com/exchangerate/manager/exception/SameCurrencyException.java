package com.exchangerate.manager.exception;

/**
 * Thrown when the requested {@code from} and {@code to} currency codes are identical, as rejected
 * by {@code ExchangeRateService.lookup}.
 */
public class SameCurrencyException extends RuntimeException {

    public SameCurrencyException(String message) {
        super(message);
    }
}
