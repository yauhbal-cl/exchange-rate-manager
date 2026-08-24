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

        CurrencyUsageRepository.CurrencyUsageProjection eurRow = usageRowOf("EUR", 3L, tie);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection firstEvent = eventRowOf("EUR", first);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection tieEvent1 = eventRowOf("EUR", tie);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection tieEvent2 = eventRowOf("EUR", tie);

        when(currencyUsageRepository.findCurrencyUsage(10, 30)).thenReturn(List.of(eurRow));
        when(currencyQueryEventRepository.findQueryTimestamps(List.of("EUR"), 30))
                .thenReturn(List.of(firstEvent, tieEvent1, tieEvent2));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(10, 30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currencyCode()).isEqualTo("EUR");
        assertThat(result.get(0).queryTimestamps()).containsExactly(first, tie, tie);
    }

    @Test
    void getUsageAnalyticsNewestTimestampEqualsLastQueriedAt() {
        Instant older = Instant.parse("2026-08-01T10:00:00Z");
        Instant newest = Instant.parse("2026-08-05T10:00:00Z");

        CurrencyUsageRepository.CurrencyUsageProjection gbpRow = usageRowOf("GBP", 2L, newest);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection olderEvent = eventRowOf("GBP", older);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection newestEvent = eventRowOf("GBP", newest);

        when(currencyUsageRepository.findCurrencyUsage(5, 90)).thenReturn(List.of(gbpRow));
        when(currencyQueryEventRepository.findQueryTimestamps(
                List.of("GBP"), UsageAnalyticsService.DEFAULT_HISTORY_WINDOW_DAYS))
                .thenReturn(List.of(olderEvent, newestEvent));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(5, 90);

        CurrencyUsageSummary summary = result.get(0);
        assertThat(summary.queryTimestamps().get(summary.queryTimestamps().size() - 1))
                .isEqualTo(summary.lastQueriedAt());
    }

    @Test
    void getUsageAnalyticsNeverQueriedCurrencyReturnsEmptyListNeverNull() {
        Instant eurTimestamp = Instant.parse("2026-08-01T10:00:00Z");

        CurrencyUsageRepository.CurrencyUsageProjection eurRow = usageRowOf("EUR", 1L, eurTimestamp);
        CurrencyUsageRepository.CurrencyUsageProjection jpyRow = usageRowOf("JPY", 0L, null);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection eurEvent = eventRowOf("EUR", eurTimestamp);

        when(currencyUsageRepository.findCurrencyUsage(null, null)).thenReturn(List.of(eurRow, jpyRow));
        when(currencyQueryEventRepository.findQueryTimestamps(
                List.of("EUR", "JPY"), UsageAnalyticsService.DEFAULT_HISTORY_WINDOW_DAYS))
                .thenReturn(List.of(eurEvent));

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

        CurrencyUsageRepository.CurrencyUsageProjection eurRow = usageRowOf("EUR", 1L, timestamp);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection eurEvent = eventRowOf("EUR", timestamp);

        when(currencyUsageRepository.findCurrencyUsage(10, 30)).thenReturn(List.of(eurRow));
        when(currencyQueryEventRepository.findQueryTimestamps(List.of("EUR"), 30))
                .thenReturn(List.of(eurEvent));

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

    @Test
    void getUsageAnalyticsWithRecentDaysPassesThatWindowToTimestampQuery() {
        Instant timestamp = Instant.parse("2026-08-01T10:00:00Z");

        CurrencyUsageRepository.CurrencyUsageProjection eurRow = usageRowOf("EUR", 4L, timestamp);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection eurEvent = eventRowOf("EUR", timestamp);

        when(currencyUsageRepository.findCurrencyUsage(10, 30)).thenReturn(List.of(eurRow));
        when(currencyQueryEventRepository.findQueryTimestamps(List.of("EUR"), 30))
                .thenReturn(List.of(eurEvent));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(10, 30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).queryTimestamps()).containsExactly(timestamp);
    }

    @Test
    void getUsageAnalyticsWithRecentDaysWiderThan90PassesWiderWindowUnclamped() {
        Instant timestamp = Instant.parse("2026-02-01T10:00:00Z");

        CurrencyUsageRepository.CurrencyUsageProjection eurRow = usageRowOf("EUR", 7L, timestamp);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection eurEvent = eventRowOf("EUR", timestamp);

        when(currencyUsageRepository.findCurrencyUsage(null, 180)).thenReturn(List.of(eurRow));
        when(currencyQueryEventRepository.findQueryTimestamps(List.of("EUR"), 180))
                .thenReturn(List.of(eurEvent));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(null, 180);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).queryTimestamps()).containsExactly(timestamp);
    }

    @Test
    void getUsageAnalyticsOmittedRecentDaysStillUsesDefaultWindow() {
        Instant timestamp = Instant.parse("2026-08-01T10:00:00Z");

        CurrencyUsageRepository.CurrencyUsageProjection eurRow = usageRowOf("EUR", 2L, timestamp);
        CurrencyQueryEventRepository.CurrencyQueryEventProjection eurEvent = eventRowOf("EUR", timestamp);

        when(currencyUsageRepository.findCurrencyUsage(null, null)).thenReturn(List.of(eurRow));
        when(currencyQueryEventRepository.findQueryTimestamps(
                List.of("EUR"), UsageAnalyticsService.DEFAULT_HISTORY_WINDOW_DAYS))
                .thenReturn(List.of(eurEvent));

        List<CurrencyUsageSummary> result = usageAnalyticsService.getUsageAnalytics(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).queryTimestamps()).containsExactly(timestamp);
    }
}
