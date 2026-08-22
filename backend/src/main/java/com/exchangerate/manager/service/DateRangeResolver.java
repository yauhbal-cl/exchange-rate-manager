package com.exchangerate.manager.service;

import com.exchangerate.manager.exception.InvalidDateRangeException;

import java.time.LocalDate;

/**
 * Resolves the effective start/end dates for a trend query, applying the default 30-day-trailing
 * window when either bound is omitted, and validating the resulting range.
 */
final class DateRangeResolver {

    private static final int DEFAULT_WINDOW_DAYS = 29;

    private DateRangeResolver() {
    }

    static DateRange resolve(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate effectiveStartDate = startDate != null ? startDate : today.minusDays(DEFAULT_WINDOW_DAYS);
        LocalDate effectiveEndDate = endDate != null ? endDate : today;

        if (effectiveStartDate.isAfter(effectiveEndDate)) {
            throw new InvalidDateRangeException(
                    "startDate " + effectiveStartDate + " must not be after endDate " + effectiveEndDate);
        }

        return new DateRange(effectiveStartDate, effectiveEndDate);
    }

    record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
