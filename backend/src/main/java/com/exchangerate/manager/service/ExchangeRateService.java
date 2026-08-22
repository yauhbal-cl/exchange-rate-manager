package com.exchangerate.manager.service;

import com.exchangerate.manager.entity.ExchangeRate;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    // 20 significant digits, HALF_UP: enough headroom past the stored scale-6 rates to avoid
    // ArithmeticException on non-terminating divisions without losing precision.
    private static final MathContext RATE_MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

    private final ExchangeRateRepository exchangeRateRepository;
    private final SpreadLookup spreadLookup;

    public ExchangeRateLookupResult lookup(String from, String to, LocalDate date) {
        LocalDate effectiveDate = date != null
                ? date
                : exchangeRateRepository.findLatestCommonDate(from, to).orElseThrow();

        ExchangeRate fromRate = exchangeRateRepository.findByCurrencyCodeAndRateDate(from, effectiveDate)
                .orElseThrow();
        ExchangeRate toRate = exchangeRateRepository.findByCurrencyCodeAndRateDate(to, effectiveDate)
                .orElseThrow();

        BigDecimal fromSpread = spreadLookup.spreadFor(from);
        BigDecimal toSpread = spreadLookup.spreadFor(to);
        BigDecimal maxSpread = fromSpread.max(toSpread);

        BigDecimal rateRatio = toRate.getRateToUsd().divide(fromRate.getRateToUsd(), RATE_MATH_CONTEXT);
        BigDecimal spreadFactor = BigDecimal.valueOf(100)
                .subtract(maxSpread)
                .divide(BigDecimal.valueOf(100), RATE_MATH_CONTEXT);
        BigDecimal rate = rateRatio.multiply(spreadFactor, RATE_MATH_CONTEXT);

        return new ExchangeRateLookupResult(from, to, rate, effectiveDate, null, null);
    }
}
