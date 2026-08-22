package com.exchangerate.manager.mapper;

import com.exchangerate.manager.api.model.CurrencyUsageEntry;
import com.exchangerate.manager.api.model.UsageAnalyticsResponse;
import com.exchangerate.manager.repository.CurrencyUsageRepository;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link CurrencyUsageRepository.CurrencyUsageProjection} rows to the generated API model
 * {@link UsageAnalyticsResponse}.
 *
 * <p>{@code currencyCode} and {@code queryCount} map 1:1 by property name. {@code lastQueriedAt}
 * needs an explicit conversion: the projection exposes it as {@link java.time.Instant}, but the
 * generated {@link CurrencyUsageEntry#getLastQueriedAt()} is
 * {@code JsonNullable<OffsetDateTime>} (the field is nullable per the OpenAPI contract), which
 * MapStruct cannot convert to automatically.
 */
@Mapper(componentModel = "spring")
public interface UsageAnalyticsMapper {

    @Mapping(
            target = "lastQueriedAt",
            expression = "java(projection.getLastQueriedAt() == null "
                    + "? org.openapitools.jackson.nullable.JsonNullable.<java.time.OffsetDateTime>undefined() "
                    + ": org.openapitools.jackson.nullable.JsonNullable.of("
                    + "projection.getLastQueriedAt().atOffset(java.time.ZoneOffset.UTC)))")
    CurrencyUsageEntry toEntry(CurrencyUsageRepository.CurrencyUsageProjection projection);

    default UsageAnalyticsResponse toResponse(List<CurrencyUsageRepository.CurrencyUsageProjection> projections) {
        return new UsageAnalyticsResponse(projections.stream().map(this::toEntry).toList());
    }
}
