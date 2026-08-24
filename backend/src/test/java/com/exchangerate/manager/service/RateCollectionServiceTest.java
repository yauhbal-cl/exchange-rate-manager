package com.exchangerate.manager.service;

import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.client.FixerClient;
import com.exchangerate.manager.client.FixerLatestResponse;
import com.exchangerate.manager.config.ExchangeRateProperties;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RateCollectionService}. Mocks {@link FixerClient} and
 * {@link ExchangeRateRepository} — no Spring context, per research.md's plain-JUnit-with-Mockito
 * approach for pure service-layer logic.
 *
 * <p>{@link ExchangeRateProperties} is a plain immutable record, not a collaborator with
 * behavior worth mocking — a real instance is constructed and wired into the service manually
 * (rather than via {@code @InjectMocks}) so the {@code baseCurrency} sanity checks in
 * {@link RateCollectionService#collect()} have a real, non-null value to compare against.
 */
@ExtendWith(MockitoExtension.class)
class RateCollectionServiceTest {

    private static final LocalDate RATE_DATE = LocalDate.of(2026, 8, 22);

    @Mock
    private FixerClient fixerClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private ExchangeRateProperties exchangeRateProperties;

    private RateCollectionService rateCollectionService;

    @BeforeEach
    void setUp() {
        exchangeRateProperties = new ExchangeRateProperties(
                "EUR",
                new BigDecimal("2.75"),
                Map.of("EUR", BigDecimal.ZERO, "default", new BigDecimal("2.75")));
        rateCollectionService =
                new RateCollectionService(fixerClient, exchangeRateRepository, exchangeRateProperties);
    }

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
        BigDecimal expectedEur = BigDecimal.ONE.divide(eurToUsd, 6, RoundingMode.HALF_UP);

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

        verify(exchangeRateRepository).upsert(
                eq("EUR"),
                argThat(bd -> bd.compareTo(expectedEur) == 0),
                eq(RATE_DATE));

        verify(exchangeRateRepository, times(4)).upsert(any(), any(), any());
    }

    @Test
    void collectTranslatesProviderFailureWithZeroWritesAttempted() {
        when(fixerClient.getLatestRates())
                .thenThrow(new FixerApiException("simulated provider failure"));

        RateCollectionException exception =
                assertThrows(RateCollectionException.class, () -> rateCollectionService.collect());

        org.assertj.core.api.Assertions.assertThat(exception)
                .hasMessage("simulated provider failure")
                .hasCauseInstanceOf(FixerApiException.class);

        verify(exchangeRateRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void collectUpsertsOnlyCurrenciesPresentInResponse() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("USD", new BigDecimal("1.080000"));
        rates.put("GBP", new BigDecimal("0.860000"));

        FixerLatestResponse response = new FixerLatestResponse();
        response.setSuccess(true);
        response.setBase("EUR");
        response.setDate(RATE_DATE);
        response.setRates(rates);

        when(fixerClient.getLatestRates()).thenReturn(response);

        rateCollectionService.collect();

        verify(exchangeRateRepository).upsert(eq("USD"), any(), eq(RATE_DATE));
        verify(exchangeRateRepository).upsert(eq("GBP"), any(), eq(RATE_DATE));
        verify(exchangeRateRepository).upsert(eq("EUR"), any(), eq(RATE_DATE));
        verify(exchangeRateRepository, times(3)).upsert(any(), any(), any());
    }

    @Test
    void collectThrowsAndWritesNothingWhenResponseBaseCurrencyIsNotExpectedBase() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("EUR", new BigDecimal("0.930000"));
        rates.put("GBP", new BigDecimal("0.790000"));

        FixerLatestResponse response = new FixerLatestResponse();
        response.setSuccess(true);
        response.setBase("USD");
        response.setDate(RATE_DATE);
        response.setRates(rates);

        when(fixerClient.getLatestRates()).thenReturn(response);

        assertThrows(RateCollectionException.class, () -> rateCollectionService.collect());

        verify(exchangeRateRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void collectThrowsAndWritesNothingWhenResponseBaseCurrencyIsNull() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("EUR", BigDecimal.ONE);
        rates.put("USD", new BigDecimal("1.080000"));

        FixerLatestResponse response = new FixerLatestResponse();
        response.setSuccess(true);
        response.setBase(null);
        response.setDate(RATE_DATE);
        response.setRates(rates);

        when(fixerClient.getLatestRates()).thenReturn(response);

        assertThrows(RateCollectionException.class, () -> rateCollectionService.collect());

        verify(exchangeRateRepository, never()).upsert(any(), any(), any());
    }

    @Test
    void collectAcceptsAndPersistsEurWhenEurAbsentFromRates() {
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
        BigDecimal expectedEur = BigDecimal.ONE.divide(eurToUsd, 6, RoundingMode.HALF_UP);
        BigDecimal expectedGbp = rates.get("GBP").divide(eurToUsd, 6, RoundingMode.HALF_UP);
        BigDecimal expectedJpy = rates.get("JPY").divide(eurToUsd, 6, RoundingMode.HALF_UP);

        verify(exchangeRateRepository).upsert(
                eq("EUR"),
                argThat(bd -> bd.compareTo(expectedEur) == 0),
                eq(RATE_DATE));

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

        verify(exchangeRateRepository, times(4)).upsert(any(), any(), any());
    }

    @Test
    void collectOverridesStaleEurSelfRateWhenPresentInRates() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("EUR", new BigDecimal("0.980000"));
        rates.put("USD", new BigDecimal("1.080000"));

        FixerLatestResponse response = new FixerLatestResponse();
        response.setSuccess(true);
        response.setBase("EUR");
        response.setDate(RATE_DATE);
        response.setRates(rates);

        when(fixerClient.getLatestRates()).thenReturn(response);

        rateCollectionService.collect();

        BigDecimal eurToUsd = rates.get("USD");
        BigDecimal expectedEur = BigDecimal.ONE.divide(eurToUsd, 6, RoundingMode.HALF_UP);

        verify(exchangeRateRepository).upsert(
                eq("EUR"),
                argThat(bd -> bd.compareTo(expectedEur) == 0),
                eq(RATE_DATE));

        verify(exchangeRateRepository).upsert(
                eq("USD"),
                argThat(bd -> bd.compareTo(new BigDecimal("1.000000")) == 0),
                eq(RATE_DATE));

        verify(exchangeRateRepository, times(2)).upsert(any(), any(), any());
    }
}
