package com.exchangerate.manager.mapper;

import com.exchangerate.manager.api.model.ExchangeRateResponse;
import com.exchangerate.manager.service.ExchangeRateLookupResult;
import org.mapstruct.Mapper;

/**
 * Maps the domain {@link ExchangeRateLookupResult} to the generated API model
 * {@link ExchangeRateResponse}.
 *
 * <p>Note: although the OpenAPI contract declares {@code rate} as
 * {@code type: string, format: decimal} (to avoid float precision loss over the wire), the
 * openapi-generator-maven-plugin resolves {@code format: decimal} to a native
 * {@link java.math.BigDecimal} on the generated model — see
 * {@code backend/target/generated-sources/openapi/.../api/model/ExchangeRateResponse.java}.
 * Both sides of this mapping are therefore {@link java.math.BigDecimal} and every field maps
 * 1:1 by matching property name, with no manual conversion required.
 */
@Mapper(componentModel = "spring")
public interface ExchangeRateResponseMapper {

    ExchangeRateResponse toResponse(ExchangeRateLookupResult result);
}
