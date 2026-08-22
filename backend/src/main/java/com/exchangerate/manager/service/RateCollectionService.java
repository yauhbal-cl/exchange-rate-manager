package com.exchangerate.manager.service;

import com.exchangerate.manager.client.FixerClient;
import com.exchangerate.manager.client.FixerLatestResponse;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * Collects the latest EUR-based rates from Fixer.io and upserts USD-based cross-rates for every
 * currency in the response.
 *
 * <p>Lets {@link com.exchangerate.manager.client.FixerApiException} propagate on failure — the
 * caller decides how to react: {@link com.exchangerate.manager.scheduler.RateCollectionScheduler}
 * logs it (a null return from {@code collect()} would otherwise be indistinguishable from
 * ShedLock skipping the method because another run already holds the lock), and
 * {@code ExchangeController} maps it to a 502 response for the manual-refresh path.
 */
@Service
public class RateCollectionService {

    private final FixerClient fixerClient;

    private final ExchangeRateRepository exchangeRateRepository;

    public RateCollectionService(FixerClient fixerClient, ExchangeRateRepository exchangeRateRepository) {
        this.fixerClient = fixerClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Transactional
    @SchedulerLock(name = "fixer-rate-collection")
    public RefreshResult collect() {
        FixerLatestResponse response = fixerClient.getLatestRates();
        Map<String, BigDecimal> rates = response.getRates();
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
