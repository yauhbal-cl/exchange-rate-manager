package com.exchangerate.manager.service;

import com.exchangerate.manager.repository.CurrencyQueryEventRepository;
import com.exchangerate.manager.repository.CurrencyUsageRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsageAnalyticsService {

    public static final int DEFAULT_HISTORY_WINDOW_DAYS = 90;

    private final CurrencyUsageRepository currencyUsageRepository;
    private final CurrencyQueryEventRepository currencyQueryEventRepository;

    /**
     * Returns one {@link CurrencyUsageSummary} per currency selected by
     * {@link CurrencyUsageRepository#findCurrencyUsage(Integer, Integer)}, enriched with that
     * currency's query-event timestamps from the last {@value #DEFAULT_HISTORY_WINDOW_DAYS} days.
     * Both repository calls observe the same transaction, so they see a consistent {@code now()}.
     */
    public List<CurrencyUsageSummary> getUsageAnalytics(Integer limit, Integer recentDays) {
        List<CurrencyUsageRepository.CurrencyUsageProjection> usageRows =
                currencyUsageRepository.findCurrencyUsage(limit, recentDays);

        if (usageRows.isEmpty()) {
            return List.of();
        }

        List<String> currencyCodes = usageRows.stream()
                .map(CurrencyUsageRepository.CurrencyUsageProjection::getCurrencyCode)
                .toList();

        List<CurrencyQueryEventRepository.CurrencyQueryEventProjection> eventRows =
                currencyQueryEventRepository.findQueryTimestamps(currencyCodes, DEFAULT_HISTORY_WINDOW_DAYS);

        Map<String, List<Instant>> timestampsByCurrencyCode = eventRows.stream()
                .collect(Collectors.groupingBy(
                        CurrencyQueryEventRepository.CurrencyQueryEventProjection::getCurrencyCode,
                        Collectors.mapping(
                                CurrencyQueryEventRepository.CurrencyQueryEventProjection::getQueriedAt,
                                Collectors.toList())));

        return usageRows.stream()
                .map(row -> new CurrencyUsageSummary(
                        row.getCurrencyCode(),
                        row.getQueryCount(),
                        row.getLastQueriedAt(),
                        timestampsByCurrencyCode.getOrDefault(row.getCurrencyCode(), List.of())))
                .toList();
    }
}
