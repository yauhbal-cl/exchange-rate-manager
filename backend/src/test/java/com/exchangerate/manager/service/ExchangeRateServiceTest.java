package com.exchangerate.manager.service;

import com.exchangerate.manager.entity.CurrencyUsage;
import com.exchangerate.manager.entity.ExchangeRate;
import com.exchangerate.manager.exception.InvalidDateRangeException;
import com.exchangerate.manager.exception.RateDataNotFoundException;
import com.exchangerate.manager.exception.SameCurrencyException;
import com.exchangerate.manager.exception.UnknownCurrencyException;
import com.exchangerate.manager.repository.CurrencyQueryEventRepository;
import com.exchangerate.manager.repository.CurrencyUsageRepository;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private CurrencyQueryEventRepository currencyQueryEventRepository;

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
    void lookupIncrementsUsageInCurrencyCodeOrderAndMapsCountsToRequestedDirection() {
        LocalDate rateDate = LocalDate.of(2026, 8, 20);

        when(exchangeRateRepository.existsByCurrencyCode("CHF")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("AUD")).thenReturn(true);
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("CHF", rateDate))
                .thenReturn(Optional.of(rateOf("CHF", new BigDecimal("1.080000"), rateDate)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("AUD", rateDate))
                .thenReturn(Optional.of(rateOf("AUD", new BigDecimal("1.000000"), rateDate)));
        when(spreadLookup.spreadFor("CHF")).thenReturn(BigDecimal.ZERO);
        when(spreadLookup.spreadFor("AUD")).thenReturn(BigDecimal.ZERO);
        when(currencyUsageRepository.findByCurrencyCode("AUD"))
                .thenReturn(Optional.of(usageOf("AUD", 11L)));
        when(currencyUsageRepository.findByCurrencyCode("CHF"))
                .thenReturn(Optional.of(usageOf("CHF", 22L)));

        ExchangeRateLookupResult result = exchangeRateService.lookup("CHF", "AUD", rateDate);

        InOrder usageOrder = inOrder(currencyUsageRepository);
        usageOrder.verify(currencyUsageRepository).incrementUsage("AUD");
        usageOrder.verify(currencyUsageRepository).findByCurrencyCode("AUD");
        usageOrder.verify(currencyUsageRepository).incrementUsage("CHF");
        usageOrder.verify(currencyUsageRepository).findByCurrencyCode("CHF");
        assertThat(result.fromCurrencyUsageCount()).isEqualTo(22L);
        assertThat(result.toCurrencyUsageCount()).isEqualTo(11L);
    }

    @Test
    void lookupRecordsOneEventPerCurrencyOnSuccess() {
        LocalDate rateDate = LocalDate.of(2026, 8, 20);

        when(exchangeRateRepository.existsByCurrencyCode("CHF")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("AUD")).thenReturn(true);
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("CHF", rateDate))
                .thenReturn(Optional.of(rateOf("CHF", new BigDecimal("1.080000"), rateDate)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("AUD", rateDate))
                .thenReturn(Optional.of(rateOf("AUD", new BigDecimal("1.000000"), rateDate)));
        when(spreadLookup.spreadFor("CHF")).thenReturn(BigDecimal.ZERO);
        when(spreadLookup.spreadFor("AUD")).thenReturn(BigDecimal.ZERO);
        when(currencyUsageRepository.findByCurrencyCode("AUD"))
                .thenReturn(Optional.of(usageOf("AUD", 11L)));
        when(currencyUsageRepository.findByCurrencyCode("CHF"))
                .thenReturn(Optional.of(usageOf("CHF", 22L)));

        exchangeRateService.lookup("CHF", "AUD", rateDate);

        // AUD/CHF alphabetical order — same pairing used for incrementUsage above.
        verify(currencyQueryEventRepository).insertEvents("AUD", "CHF");
    }

    @Test
    void lookupCalledFiveTimesRecordsFiveEventCallsForSamePair() {
        LocalDate rateDate = LocalDate.of(2026, 8, 20);

        when(exchangeRateRepository.existsByCurrencyCode("CHF")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("AUD")).thenReturn(true);
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("CHF", rateDate))
                .thenReturn(Optional.of(rateOf("CHF", new BigDecimal("1.080000"), rateDate)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("AUD", rateDate))
                .thenReturn(Optional.of(rateOf("AUD", new BigDecimal("1.000000"), rateDate)));
        when(spreadLookup.spreadFor("CHF")).thenReturn(BigDecimal.ZERO);
        when(spreadLookup.spreadFor("AUD")).thenReturn(BigDecimal.ZERO);
        when(currencyUsageRepository.findByCurrencyCode("AUD"))
                .thenReturn(Optional.of(usageOf("AUD", 11L)));
        when(currencyUsageRepository.findByCurrencyCode("CHF"))
                .thenReturn(Optional.of(usageOf("CHF", 22L)));

        for (int i = 0; i < 5; i++) {
            exchangeRateService.lookup("CHF", "AUD", rateDate);
        }

        // Mirrors how incrementUsage("AUD")/incrementUsage("CHF") would also each grow by 5 —
        // one query-event pair is recorded per lookup call, same as the usage counters.
        verify(currencyQueryEventRepository, times(5)).insertEvents("AUD", "CHF");
    }

    @Test
    void lookupWithSameCurrencyDoesNotRecordAnyEvent() {
        assertThatThrownBy(() -> exchangeRateService.lookup("EUR", "EUR", LocalDate.of(2026, 8, 20)))
                .isInstanceOf(SameCurrencyException.class);

        verifyNoInteractions(currencyQueryEventRepository);
    }

    @Test
    void lookupWithUnknownCurrencyDoesNotRecordAnyEvent() {
        when(exchangeRateRepository.existsByCurrencyCode("XXX")).thenReturn(false);

        assertThatThrownBy(() -> exchangeRateService.lookup("XXX", "USD", LocalDate.of(2026, 8, 20)))
                .isInstanceOf(UnknownCurrencyException.class);

        verifyNoInteractions(currencyQueryEventRepository);
    }

    @Test
    void lookupWithNoRateDataForDateDoesNotRecordAnyEvent() {
        LocalDate rateDate = LocalDate.of(2026, 8, 20);

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("USD")).thenReturn(true);
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("EUR", rateDate))
                .thenReturn(Optional.of(rateOf("EUR", new BigDecimal("1.080000"), rateDate)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDate("USD", rateDate))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeRateService.lookup("EUR", "USD", rateDate))
                .isInstanceOf(RateDataNotFoundException.class);

        verifyNoInteractions(currencyQueryEventRepository);
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

    private static ExchangeRateRepository.RateTrendProjection trendRowOf(
            LocalDate rateDate, BigDecimal fromRateToUsd, BigDecimal toRateToUsd) {
        ExchangeRateRepository.RateTrendProjection row = mock(ExchangeRateRepository.RateTrendProjection.class);
        when(row.getRateDate()).thenReturn(rateDate);
        when(row.getFromRateToUsd()).thenReturn(fromRateToUsd);
        when(row.getToRateToUsd()).thenReturn(toRateToUsd);
        return row;
    }

    @Test
    void getTrendWithoutDatesDefaultsToLast30DayWindow() {
        LocalDate today = LocalDate.now();
        LocalDate expectedStart = today.minusDays(29);

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("USD")).thenReturn(true);
        when(spreadLookup.spreadFor("EUR")).thenReturn(BigDecimal.ZERO);
        when(spreadLookup.spreadFor("USD")).thenReturn(BigDecimal.ZERO);
        when(exchangeRateRepository.findTrend(eq("EUR"), eq("USD"), any(), any()))
                .thenReturn(List.of());

        exchangeRateService.getTrend("EUR", "USD", null, null);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(exchangeRateRepository).findTrend(eq("EUR"), eq("USD"), startCaptor.capture(), endCaptor.capture());

        assertThat(startCaptor.getValue()).isEqualTo(expectedStart);
        assertThat(endCaptor.getValue()).isEqualTo(today);
    }

    @Test
    void getTrendWithUnknownFromCurrencyThrows() {
        when(exchangeRateRepository.existsByCurrencyCode("XXX")).thenReturn(false);

        assertThatThrownBy(() -> exchangeRateService.getTrend("XXX", "USD", null, null))
                .isInstanceOf(UnknownCurrencyException.class)
                .hasMessage("Unknown currency code: XXX");
    }

    @Test
    void getTrendWithUnknownToCurrencyThrows() {
        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("XXX")).thenReturn(false);

        assertThatThrownBy(() -> exchangeRateService.getTrend("EUR", "XXX", null, null))
                .isInstanceOf(UnknownCurrencyException.class)
                .hasMessage("Unknown currency code: XXX");
    }

    @Test
    void getTrendWithStartDateAfterEndDateThrows() {
        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("USD")).thenReturn(true);

        LocalDate startDate = LocalDate.of(2026, 8, 20);
        LocalDate endDate = LocalDate.of(2026, 8, 10);

        assertThatThrownBy(() -> exchangeRateService.getTrend("EUR", "USD", startDate, endDate))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void getTrendComputesSpreadAdjustedRateUsingSameFormulaAsLookup() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 3);
        BigDecimal eurToUsd = new BigDecimal("1.080000");
        BigDecimal gbpToUsd = new BigDecimal("1.260000");
        BigDecimal eurSpread = new BigDecimal("2.75");
        BigDecimal gbpSpread = new BigDecimal("3.25");

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("GBP")).thenReturn(true);
        when(spreadLookup.spreadFor("EUR")).thenReturn(eurSpread);
        when(spreadLookup.spreadFor("GBP")).thenReturn(gbpSpread);

        ExchangeRateRepository.RateTrendProjection row =
                trendRowOf(LocalDate.of(2026, 8, 2), eurToUsd, gbpToUsd);
        when(exchangeRateRepository.findTrend("EUR", "GBP", startDate, endDate))
                .thenReturn(List.of(row));

        List<RateTrendPoint> result = exchangeRateService.getTrend("EUR", "GBP", startDate, endDate);

        BigDecimal expected = expectedRate(eurToUsd, gbpToUsd, eurSpread.max(gbpSpread));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rateDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(result.get(0).rate()).isEqualByComparingTo(expected);
    }

    @Test
    void getTrendPreservesOrderOfRepositoryResults() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 5);
        BigDecimal eurSpread = new BigDecimal("2.75");
        BigDecimal gbpSpread = new BigDecimal("3.25");

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("GBP")).thenReturn(true);
        when(spreadLookup.spreadFor("EUR")).thenReturn(eurSpread);
        when(spreadLookup.spreadFor("GBP")).thenReturn(gbpSpread);

        LocalDate day1 = LocalDate.of(2026, 8, 1);
        LocalDate day2 = LocalDate.of(2026, 8, 2);
        LocalDate day3 = LocalDate.of(2026, 8, 3);

        ExchangeRateRepository.RateTrendProjection row1 =
                trendRowOf(day1, new BigDecimal("1.080000"), new BigDecimal("1.260000"));
        ExchangeRateRepository.RateTrendProjection row2 =
                trendRowOf(day2, new BigDecimal("1.081000"), new BigDecimal("1.261000"));
        ExchangeRateRepository.RateTrendProjection row3 =
                trendRowOf(day3, new BigDecimal("1.082000"), new BigDecimal("1.262000"));

        when(exchangeRateRepository.findTrend("EUR", "GBP", startDate, endDate))
                .thenReturn(List.of(row1, row2, row3));

        List<RateTrendPoint> result = exchangeRateService.getTrend("EUR", "GBP", startDate, endDate);

        assertThat(result).extracting(RateTrendPoint::rateDate)
                .containsExactly(day1, day2, day3);
    }

    @Test
    void getTrendNeverInteractsWithCurrencyUsageRepository() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 3);

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("GBP")).thenReturn(true);
        when(spreadLookup.spreadFor("EUR")).thenReturn(BigDecimal.ZERO);
        when(spreadLookup.spreadFor("GBP")).thenReturn(BigDecimal.ZERO);
        when(exchangeRateRepository.findTrend("EUR", "GBP", startDate, endDate))
                .thenReturn(List.of());

        exchangeRateService.getTrend("EUR", "GBP", startDate, endDate);

        verifyNoInteractions(currencyUsageRepository);
    }
}
