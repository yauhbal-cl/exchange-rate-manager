package com.exchangerate.manager.service;

import com.exchangerate.manager.entity.ExchangeRate;
import com.exchangerate.manager.exception.RateDataNotFoundException;
import com.exchangerate.manager.exception.SameCurrencyException;
import com.exchangerate.manager.exception.UnknownCurrencyException;
import com.exchangerate.manager.repository.CurrencyUsageRepository;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CurrencyUsageRepository currencyUsageRepository;
    private final SpreadLookup spreadLookup;

    @Transactional
    public ExchangeRateLookupResult lookup(String from, String to, LocalDate date) {
        if (from.equals(to)) {
            throw new SameCurrencyException(
                    "The same currency code '" + from + "' was supplied for both 'from' and 'to'");
        }
        if (!exchangeRateRepository.existsByCurrencyCode(from)) {
            throw new UnknownCurrencyException("Unknown currency code: " + from);
        }
        if (!exchangeRateRepository.existsByCurrencyCode(to)) {
            throw new UnknownCurrencyException("Unknown currency code: " + to);
        }

        LocalDate effectiveDate = date != null
                ? date
                : exchangeRateRepository.findLatestCommonDate(from, to)
                        .orElseThrow(() -> new RateDataNotFoundException(
                                "No common rate date found for currencies '" + from + "' and '" + to + "'"));

        ExchangeRate fromRate = exchangeRateRepository.findByCurrencyCodeAndRateDate(from, effectiveDate)
                .orElseThrow(() -> new RateDataNotFoundException(
                        "No rate data found for currency '" + from + "' on date " + effectiveDate));
        ExchangeRate toRate = exchangeRateRepository.findByCurrencyCodeAndRateDate(to, effectiveDate)
                .orElseThrow(() -> new RateDataNotFoundException(
                        "No rate data found for currency '" + to + "' on date " + effectiveDate));

        BigDecimal fromSpread = spreadLookup.spreadFor(from);
        BigDecimal toSpread = spreadLookup.spreadFor(to);
        BigDecimal maxSpread = fromSpread.max(toSpread);

        BigDecimal rateRatio = toRate.getRateToUsd().divide(fromRate.getRateToUsd(), RATE_MATH_CONTEXT);
        BigDecimal spreadFactor = BigDecimal.valueOf(100)
                .subtract(maxSpread)
                .divide(BigDecimal.valueOf(100), RATE_MATH_CONTEXT);
        BigDecimal rate = rateRatio.multiply(spreadFactor, RATE_MATH_CONTEXT);

        currencyUsageRepository.incrementUsage(from);
        Long fromCurrencyUsageCount = currencyUsageRepository.findByCurrencyCode(from)
                .orElseThrow()
                .getQueryCount();
        currencyUsageRepository.incrementUsage(to);
        Long toCurrencyUsageCount = currencyUsageRepository.findByCurrencyCode(to)
                .orElseThrow()
                .getQueryCount();

        return new ExchangeRateLookupResult(
                from, to, rate, effectiveDate, fromCurrencyUsageCount, toCurrencyUsageCount);
    }
}
