package com.exchangerate.manager.service;

import com.exchangerate.manager.repository.CurrencyQueryEventRepository;
import com.exchangerate.manager.repository.CurrencyUsageRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link UsageAnalyticsService}. Mocks {@link CurrencyUsageRepository} and
 * {@link CurrencyQueryEventRepository} — no Spring context, per house style (see
 * {@code ExchangeRateServiceTest}). {@code UsageAnalyticsService} does not exist yet; this test
 * is written against its intended contract ahead of implementation (TDD) and will not compile
 * until that class lands.
 */
@ExtendWith(MockitoExtension.class)
class UsageAnalyticsServiceTest {

    @Mock
    private CurrencyUsageRepository currencyUsageRepository;

    @Mock
    private CurrencyQueryEventRepository currencyQueryEventRepository;

    @InjectMocks
    private UsageAnalyticsService usageAnalyticsService;

    private static CurrencyUsageRepository.CurrencyUsageProjection usageRowOf(
            String currencyCode, long queryCount, Instant lastQueriedAt) {
        CurrencyUsageRepository.CurrencyUsageProjection row =
                mock(CurrencyUsageRepository.CurrencyUsageProjection.class);
        when(row.getCurrencyCode()).thenReturn(currencyCode);
        when(row.getQueryCount()).thenReturn(queryCount);
        when(row.getLastQueriedAt()).thenReturn(lastQueriedAt);
        return row;
    }

    private static CurrencyQueryEventRepository.CurrencyQueryEventProjection eventRowOf(
            String currencyCode, Instant queriedAt) {
        CurrencyQueryEventRepository.CurrencyQueryEventProjection row =
                mock(CurrencyQueryEventRepository.CurrencyQueryEventProjection.class);
        when(row.getCurrencyCode()).thenReturn(currencyCode);
        when(row.getQueriedAt()).thenReturn(queriedAt);
        return row;
    }

    @Test
    void getUsageAnalyticsReturnsChronologicalTimestampsWithIdTieBreakOrderPreserved() {
        Instant first = Instant.parse("2026-08-01T10:00:00Z");
        Instant tie = Instant.parse("2026-08-02T10:00:00Z");

        when(currencyUsageRepository.findCurrencyUsage(10, 30))
                .thenReturn(List.of(usageRowOf("EUR", 3L, tie)));
        when(currencyQueryEventRepository.findQueryTimestamps(
                List.of("EUR"), UsageAnalyticsService.DEFAULT_HISTORY_WINDOW_DAYS))
                .thenReturn(List.of(
                        eventRowOf("EUR", first),
                        eventRowOf("EUR", tie),
                        eventRowOf("EUR", tie)));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(10, 30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currencyCode()).isEqualTo("EUR");
        assertThat(result.get(0).queryTimestamps()).containsExactly(first, tie, tie);
    }

    @Test
    void getUsageAnalyticsNewestTimestampEqualsLastQueriedAt() {
        Instant older = Instant.parse("2026-08-01T10:00:00Z");
        Instant newest = Instant.parse("2026-08-05T10:00:00Z");

        when(currencyUsageRepository.findCurrencyUsage(5, 90))
                .thenReturn(List.of(usageRowOf("GBP", 2L, newest)));
        when(currencyQueryEventRepository.findQueryTimestamps(
                List.of("GBP"), UsageAnalyticsService.DEFAULT_HISTORY_WINDOW_DAYS))
                .thenReturn(List.of(eventRowOf("GBP", older), eventRowOf("GBP", newest)));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(5, 90);

        CurrencyUsageSummary summary = result.get(0);
        assertThat(summary.queryTimestamps().get(summary.queryTimestamps().size() - 1))
                .isEqualTo(summary.lastQueriedAt());
    }

    @Test
    void getUsageAnalyticsNeverQueriedCurrencyReturnsEmptyListNeverNull() {
        Instant eurTimestamp = Instant.parse("2026-08-01T10:00:00Z");

        when(currencyUsageRepository.findCurrencyUsage(null, null))
                .thenReturn(List.of(
                        usageRowOf("EUR", 1L, eurTimestamp),
                        usageRowOf("JPY", 0L, null)));
        when(currencyQueryEventRepository.findQueryTimestamps(
                List.of("EUR", "JPY"), UsageAnalyticsService.DEFAULT_HISTORY_WINDOW_DAYS))
                .thenReturn(List.of(eventRowOf("EUR", eurTimestamp)));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(null, null);

        CurrencyUsageSummary jpySummary = result.stream()
                .filter(summary -> summary.currencyCode().equals("JPY"))
                .findFirst()
                .orElseThrow();

        assertThat(jpySummary.queryTimestamps()).isEmpty();
        assertThat(jpySummary.lastQueriedAt()).isNull();
    }

    @Test
    void getUsageAnalyticsReturnsByteIdenticalResultsForRepeatedIdenticalRequests() {
        Instant timestamp = Instant.parse("2026-08-01T10:00:00Z");

        when(currencyUsageRepository.findCurrencyUsage(10, 30))
                .thenReturn(List.of(usageRowOf("EUR", 1L, timestamp)));
        when(currencyQueryEventRepository.findQueryTimestamps(
                List.of("EUR"), UsageAnalyticsService.DEFAULT_HISTORY_WINDOW_DAYS))
                .thenReturn(List.of(eventRowOf("EUR", timestamp)));

        List<CurrencyUsageSummary> firstCall = usageAnalyticsService.getUsageAnalytics(10, 30);
        List<CurrencyUsageSummary> secondCall = usageAnalyticsService.getUsageAnalytics(10, 30);

        assertThat(firstCall).isEqualTo(secondCall);
    }

    @Test
    void getUsageAnalyticsSkipsHistoryQueryWhenSelectionIsEmpty() {
        when(currencyUsageRepository.findCurrencyUsage(10, 30)).thenReturn(List.of());

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(10, 30);

        assertThat(result).isEmpty();
        verify(currencyQueryEventRepository, never()).findQueryTimestamps(any(), any());
    }
}
