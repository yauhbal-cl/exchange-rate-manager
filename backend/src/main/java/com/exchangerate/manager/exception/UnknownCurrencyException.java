package com.exchangerate.manager.exception;

/**
 * Thrown when a requested currency code has no exchange_rates row at all (neither as a source nor
 * a target currency), as detected by {@code ExchangeRateService.lookup}.
 */
public class UnknownCurrencyException extends RuntimeException {

    public UnknownCurrencyException(String message) {
        super(message);
    }
}
