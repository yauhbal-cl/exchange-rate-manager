package com.exchangerate.manager.service;

import com.exchangerate.manager.config.ExchangeRateProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class SpreadLookup {

    private final ExchangeRateProperties properties;

    public BigDecimal spreadFor(String currencyCode) {
        return properties.spreads().getOrDefault(currencyCode, properties.defaultSpreadPercent());
    }
}
