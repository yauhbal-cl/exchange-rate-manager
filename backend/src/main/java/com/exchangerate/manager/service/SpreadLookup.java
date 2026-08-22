package com.exchangerate.manager.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class SpreadLookup {

    private static final String DEFAULT_KEY = "DEFAULT";

    private static final Map<String, BigDecimal> SPREADS = Map.ofEntries(
            Map.entry("USD", new BigDecimal("0.00")),
            Map.entry("JPY", new BigDecimal("3.25")),
            Map.entry("HKD", new BigDecimal("3.25")),
            Map.entry("KRW", new BigDecimal("3.25")),
            Map.entry("MYR", new BigDecimal("4.50")),
            Map.entry("INR", new BigDecimal("4.50")),
            Map.entry("MXN", new BigDecimal("4.50")),
            Map.entry("RUB", new BigDecimal("6.00")),
            Map.entry("CNY", new BigDecimal("6.00")),
            Map.entry("ZAR", new BigDecimal("6.00")),
            Map.entry(DEFAULT_KEY, new BigDecimal("2.75")));

    public BigDecimal spreadFor(String currencyCode) {
        String key = currencyCode == null ? DEFAULT_KEY : currencyCode.toUpperCase();
        return SPREADS.getOrDefault(key, SPREADS.get(DEFAULT_KEY));
    }
}
