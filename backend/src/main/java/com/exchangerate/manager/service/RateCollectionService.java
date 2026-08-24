package com.exchangerate.manager.service;

import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.client.FixerClient;
import com.exchangerate.manager.client.FixerLatestResponse;
import com.exchangerate.manager.config.ExchangeRateProperties;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import lombok.RequiredArgsConstructor;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects the latest EUR-based rates from Fixer.io and upserts USD-based cross-rates for every
 * currency in the response.
 *
 * <p>Provider-specific failures are translated to {@link RateCollectionException} so callers do
 * not depend on details of the configured rate provider.
 */
@Service
@RequiredArgsConstructor
public class RateCollectionService {

    private final FixerClient fixerClient;

    private final ExchangeRateRepository exchangeRateRepository;

    private final ExchangeRateProperties exchangeRateProperties;

    @Transactional
    @SchedulerLock(name = "fixer-rate-collection")
    public RefreshResult collect() {
        FixerLatestResponse response;
        try {
            response = fixerClient.getLatestRates();
        } catch (FixerApiException e) {
            throw new RateCollectionException(e.getMessage(), e);
        }

        String expectedBaseCurrency = exchangeRateProperties.baseCurrency();
        String actualBaseCurrency = response.getBase();
        if (actualBaseCurrency == null
                || actualBaseCurrency.isBlank()
                || !actualBaseCurrency.equals(expectedBaseCurrency)) {
            throw new RateCollectionException(
                    "Fixer.io /latest call returned unexpected base currency: expected '"
                            + expectedBaseCurrency + "' but got '" + actualBaseCurrency + "'");
        }

        Map<String, BigDecimal> rates = new LinkedHashMap<>(response.getRates());
        rates.put(expectedBaseCurrency, BigDecimal.ONE);

        LocalDate rateDate = response.getDate();
        BigDecimal eurToUsd = rates.get("USD");

        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            String currencyCode = entry.getKey();
            BigDecimal eurToX = entry.getValue();
            BigDecimal rateToUsd = eurToX.divide(eurToUsd, 6, RoundingMode.HALF_UP);
            exchangeRateRepository.upsert(currencyCode, rateToUsd, rateDate);
        }

        return new RefreshResult(rates.size(), rateDate);
    }
}
