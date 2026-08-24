package com.exchangerate.manager.mapper;

import com.exchangerate.manager.api.model.CurrencyUsageEntry;
import com.exchangerate.manager.api.model.UsageAnalyticsResponse;
import com.exchangerate.manager.service.CurrencyUsageSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link CurrencyUsageSummary} rows to the generated API model {@link
 * UsageAnalyticsResponse}.
 *
 * <p>{@code currencyCode} and {@code queryCount} map 1:1 by property name. {@code lastQueriedAt}
 * needs an explicit conversion: the summary exposes it as {@link java.time.Instant}, but the
 * generated {@link CurrencyUsageEntry#getLastQueriedAt()} is
 * {@code JsonNullable<OffsetDateTime>} (the field is nullable per the OpenAPI contract), which
 * MapStruct cannot convert to automatically. {@code queryTimestamps} needs a similar explicit
 * conversion from {@code List<Instant>} to {@code List<OffsetDateTime>}, but — unlike
 * {@code lastQueriedAt} — the generated field is a required, non-nullable list with no
 * {@code JsonNullable} wrapper.
 */
@Mapper(componentModel = "spring")
public interface UsageAnalyticsMapper {

    @Mapping(
            target = "lastQueriedAt",
            expression = "java(summary.lastQueriedAt() == null "
                    + "? org.openapitools.jackson.nullable.JsonNullable.<java.time.OffsetDateTime>undefined() "
                    + ": org.openapitools.jackson.nullable.JsonNullable.of("
                    + "summary.lastQueriedAt().atOffset(java.time.ZoneOffset.UTC)))")
    @Mapping(
            target = "queryTimestamps",
            expression = "java(summary.queryTimestamps().stream()"
                    + ".map(instant -> instant.atOffset(java.time.ZoneOffset.UTC))"
                    + ".toList())")
    CurrencyUsageEntry toEntry(CurrencyUsageSummary summary);

    default UsageAnalyticsResponse toResponse(List<CurrencyUsageSummary> summaries) {
        return new UsageAnalyticsResponse(summaries.stream().map(this::toEntry).toList());
    }
}
