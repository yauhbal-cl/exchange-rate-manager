package com.exchangerate.manager.service;

import com.exchangerate.manager.entity.CurrencyUsage;
import com.exchangerate.manager.entity.ExchangeRate;
import com.exchangerate.manager.repository.CurrencyUsageRepository;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ExchangeRateService}. Mocks {@link ExchangeRateRepository} and
 * {@link SpreadLookup} — no Spring context, per research.md's plain-JUnit-with-Mockito approach
 * for pure service-layer logic. Covers only the happy paths of US1 (Acceptance Scenarios 1-3);
 * validation/error handling and usage-counter increments are not yet implemented in the service
 * and are covered by later tasks.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final MathContext RATE_MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private SpreadLookup spreadLookup;

    @Mock
    private CurrencyUsageRepository currencyUsageRepository;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    private static ExchangeRate rateOf(String currencyCode, BigDecimal rateToUsd, LocalDate rateDate) {
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.setCurrencyCode(currencyCode);
        exchangeRate.setRateToUsd(rateToUsd);
        exchangeRate.setRateDate(rateDate);
        return exchangeRate;
    }

    private static CurrencyUsage usageOf(String currencyCode, long queryCount) {
        CurrencyUsage currencyUsage = new CurrencyUsage();
        currencyUsage.setCurrencyCode(currencyCode);
        currencyUsage.setQueryCount(queryCount);
        return currencyUsage;
    }

    private static BigDecimal expectedRate(BigDecimal fromRateToUsd, BigDecimal toRateToUsd, BigDecimal maxSpread) {
        BigDecimal rateRatio = toRateToUsd.divide(fromRateToUsd, RATE_MATH_CONTEXT);
        BigDecimal spreadFactor = BigDecimal.valueOf(100)
                .subtract(maxSpread)
                .divide(BigDecimal.valueOf(100), RATE_MATH_CONTEXT);
        return rateRatio.multiply(spreadFactor, RATE_MATH_CONTEXT);
    }

    @Test
    void lookupWithExplicitDateComputesSpreadAdjustedRate() {
        LocalDate rateDate = LocalDate.of(2026, 8, 20);
        BigDecimal eurToUsd = new BigDecimal("1.080000");
        BigDecimal gbpToUsd = new BigDecimal("1.260000");
        BigDecimal eurSpread = new BigDecimal("2.75");
        BigDecimal gbpSpread = new BigDecimal("3.25");

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("GBP")).thenReturn(true);
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("EUR", rateDate))
                .thenReturn(Optional.of(rateOf("EUR", eurToUsd, rateDate)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("GBP", rateDate))
                .thenReturn(Optional.of(rateOf("GBP", gbpToUsd, rateDate)));
        when(spreadLookup.spreadFor("EUR")).thenReturn(eurSpread);
        when(spreadLookup.spreadFor("GBP")).thenReturn(gbpSpread);
        when(currencyUsageRepository.findByCurrencyCode("EUR"))
                .thenReturn(Optional.of(usageOf("EUR", 1L)));
        when(currencyUsageRepository.findByCurrencyCode("GBP"))
                .thenReturn(Optional.of(usageOf("GBP", 1L)));

        ExchangeRateLookupResult result = exchangeRateService.lookup("EUR", "GBP", rateDate);

        BigDecimal expected = expectedRate(eurToUsd, gbpToUsd, gbpSpread.max(eurSpread));

        assertThat(result.rate()).isEqualByComparingTo(expected);
        assertThat(result.rateDate()).isEqualTo(rateDate);
        assertThat(result.fromCurrency()).isEqualTo("EUR");
        assertThat(result.toCurrency()).isEqualTo("GBP");
    }

    @Test
    void lookupWithBaseCurrencyZeroSpreadUsesOnlyTheNonBaseSpread() {
        LocalDate rateDate = LocalDate.of(2026, 8, 20);
        BigDecimal usdToUsd = new BigDecimal("1.000000");
        BigDecimal jpyToUsd = new BigDecimal("0.006700");
        BigDecimal usdSpread = BigDecimal.ZERO;
        BigDecimal jpySpread = new BigDecimal("3.25");

        when(exchangeRateRepository.existsByCurrencyCode("USD")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("JPY")).thenReturn(true);
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("USD", rateDate))
                .thenReturn(Optional.of(rateOf("USD", usdToUsd, rateDate)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("JPY", rateDate))
                .thenReturn(Optional.of(rateOf("JPY", jpyToUsd, rateDate)));
        when(spreadLookup.spreadFor("USD")).thenReturn(usdSpread);
        when(spreadLookup.spreadFor("JPY")).thenReturn(jpySpread);
        when(currencyUsageRepository.findByCurrencyCode("USD"))
                .thenReturn(Optional.of(usageOf("USD", 1L)));
        when(currencyUsageRepository.findByCurrencyCode("JPY"))
                .thenReturn(Optional.of(usageOf("JPY", 1L)));

        ExchangeRateLookupResult result = exchangeRateService.lookup("USD", "JPY", rateDate);

        BigDecimal expected = expectedRate(usdToUsd, jpyToUsd, jpySpread);

        assertThat(result.rate()).isEqualByComparingTo(expected);
        assertThat(result.rateDate()).isEqualTo(rateDate);
    }

    @Test
    void lookupWithoutDateResolvesLatestCommonDateFromRepository() {
        LocalDate resolvedDate = LocalDate.of(2026, 8, 19);
        BigDecimal eurToUsd = new BigDecimal("1.080000");
        BigDecimal usdToUsd = new BigDecimal("1.000000");
        BigDecimal eurSpread = new BigDecimal("2.75");
        BigDecimal usdSpread = BigDecimal.ZERO;

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("USD")).thenReturn(true);
        when(exchangeRateRepository.findLatestCommonDate("EUR", "USD"))
                .thenReturn(Optional.of(resolvedDate));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("EUR", resolvedDate))
                .thenReturn(Optional.of(rateOf("EUR", eurToUsd, resolvedDate)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("USD", resolvedDate))
                .thenReturn(Optional.of(rateOf("USD", usdToUsd, resolvedDate)));
        when(spreadLookup.spreadFor("EUR")).thenReturn(eurSpread);
        when(spreadLookup.spreadFor("USD")).thenReturn(usdSpread);
        when(currencyUsageRepository.findByCurrencyCode("EUR"))
                .thenReturn(Optional.of(usageOf("EUR", 1L)));
        when(currencyUsageRepository.findByCurrencyCode("USD"))
                .thenReturn(Optional.of(usageOf("USD", 1L)));

        ExchangeRateLookupResult result = exchangeRateService.lookup("EUR", "USD", null);

        verify(exchangeRateRepository).findLatestCommonDate("EUR", "USD");
        verify(exchangeRateRepository).findByCurrencyCodeAndRateDate("EUR", resolvedDate);
        verify(exchangeRateRepository).findByCurrencyCodeAndRateDate("USD", resolvedDate);

        assertThat(result.rateDate()).isEqualTo(resolvedDate);

        BigDecimal expected = expectedRate(eurToUsd, usdToUsd, eurSpread.max(usdSpread));
        assertThat(result.rate()).isEqualByComparingTo(expected);
    }
}
