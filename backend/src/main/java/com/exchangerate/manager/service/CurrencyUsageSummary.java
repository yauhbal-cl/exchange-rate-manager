package com.exchangerate.manager.service;

import java.time.Instant;
import java.util.List;

// Single input type the future UsageAnalyticsMapper maps to the wire CurrencyUsageEntry.
public record CurrencyUsageSummary(
        String currencyCode,
        long queryCount,
        Instant lastQueriedAt,
        List<Instant> queryTimestamps) {
}
