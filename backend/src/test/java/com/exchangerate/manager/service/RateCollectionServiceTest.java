package com.exchangerate.manager.service;

import com.exchangerate.manager.client.FixerClient;
import com.exchangerate.manager.client.FixerLatestResponse;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RateCollectionService}. Mocks {@link FixerClient} and
 * {@link ExchangeRateRepository} — no Spring context, per research.md's plain-JUnit-with-Mockito
 * approach for pure service-layer logic.
 */
@ExtendWith(MockitoExtension.class)
class RateCollectionServiceTest {

    private static final LocalDate RATE_DATE = LocalDate.of(2026, 8, 22);

    @Mock
    private FixerClient fixerClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private RateCollectionService rateCollectionService;

    @Test
    void collectsAndUpsertsCrossRatesToUsdForEveryCurrencyInResponse() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("USD", new BigDecimal("1.080000"));
        rates.put("GBP", new BigDecimal("0.860000"));
        rates.put("JPY", new BigDecimal("160.500000"));

        FixerLatestResponse response = new FixerLatestResponse();
        response.setSuccess(true);
        response.setBase("EUR");
        response.setDate(RATE_DATE);
        response.setRates(rates);

        when(fixerClient.getLatestRates()).thenReturn(response);

        rateCollectionService.collect();

        BigDecimal eurToUsd = rates.get("USD");
        BigDecimal expectedGbp = rates.get("GBP").divide(eurToUsd, 6, RoundingMode.HALF_UP);
        BigDecimal expectedJpy = rates.get("JPY").divide(eurToUsd, 6, RoundingMode.HALF_UP);

        verify(exchangeRateRepository).upsert(
                eq("USD"),
                argThat(bd -> bd.compareTo(new BigDecimal("1.000000")) == 0),
                eq(RATE_DATE));

        verify(exchangeRateRepository).upsert(
                eq("GBP"),
                argThat(bd -> bd.compareTo(expectedGbp) == 0),
                eq(RATE_DATE));

        verify(exchangeRateRepository).upsert(
                eq("JPY"),
                argThat(bd -> bd.compareTo(expectedJpy) == 0),
                eq(RATE_DATE));

        verify(exchangeRateRepository, times(3)).upsert(any(), any(), any());
    }
}
