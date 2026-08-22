package com.exchangerate.manager.mapper;

import com.exchangerate.manager.api.model.ExchangeRateTrendResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps the domain {@link com.exchangerate.manager.service.RateTrendPoint} list, together with the
 * requested currency pair, to the generated API model {@link ExchangeRateTrendResponse}.
 *
 * <p>{@code fromCurrency}/{@code toCurrency} are not part of the service's trend result (the
 * service only returns the points for an already-known pair), so they are supplied as separate
 * method parameters and wired explicitly via {@link Mapping#source()} referencing the parameter
 * name.
 */
@Mapper(componentModel = "spring")
public interface ExchangeRateTrendResponseMapper {

    @Mapping(target = "fromCurrency", source = "fromCurrency")
    @Mapping(target = "toCurrency", source = "toCurrency")
    @Mapping(target = "points", source = "points")
    ExchangeRateTrendResponse toResponse(
            String fromCurrency, String toCurrency, List<com.exchangerate.manager.service.RateTrendPoint> points);

    com.exchangerate.manager.api.model.RateTrendPoint toApiModel(
            com.exchangerate.manager.service.RateTrendPoint point);
}
