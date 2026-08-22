package com.exchangerate.manager.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateLookupResult(
    String fromCurrency,
    String toCurrency,
    BigDecimal rate,
    LocalDate rateDate,
    Long fromCurrencyUsageCount,
    Long toCurrencyUsageCount
) {}
