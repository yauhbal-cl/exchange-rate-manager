package com.exchangerate.manager.service;

import java.time.LocalDate;

/**
 * Result of a successful AI-generated trend narrative: the resolved (default-applied) date range
 * actually summarized, and the model-generated narrative grounded in that range's historical
 * rates.
 */
public record TrendInsightResult(
    String fromCurrency,
    String toCurrency,
    LocalDate startDate,
    LocalDate endDate,
    String narrative
) {}
