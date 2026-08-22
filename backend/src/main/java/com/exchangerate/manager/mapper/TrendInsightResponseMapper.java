package com.exchangerate.manager.mapper;

import com.exchangerate.manager.api.model.TrendInsightResponse;
import com.exchangerate.manager.service.TrendInsightResult;
import org.mapstruct.Mapper;

/**
 * Maps the domain {@link TrendInsightResult} to the generated API model {@link
 * TrendInsightResponse}.
 *
 * <p>{@code fromCurrency}, {@code toCurrency}, {@code startDate}, {@code endDate}, and {@code
 * narrative} line up 1:1 by name and type, so MapStruct's automatic property matching handles the
 * mapping without any explicit {@code @Mapping} annotations.
 */
@Mapper(componentModel = "spring")
public interface TrendInsightResponseMapper {

    TrendInsightResponse toResponse(TrendInsightResult result);
}
