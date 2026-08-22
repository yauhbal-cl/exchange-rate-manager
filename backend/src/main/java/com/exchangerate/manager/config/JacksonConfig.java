package com.exchangerate.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link JsonNullableJackson3Module} so the generated API models'
 * {@code JsonNullable<T>} fields (used for OpenAPI-nullable properties, e.g.
 * {@code CurrencyUsageEntry.lastQueriedAt}) serialize as a plain JSON {@code null} instead of the
 * wrapper's raw {@code {"present":false}} shape. See that class's Javadoc for why the upstream
 * {@code org.openapitools:jackson-databind-nullable} module (Jackson 2) can't be used directly on
 * Spring Boot 4's Jackson 3 {@code ObjectMapper}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonNullableJackson3Module jsonNullableJackson3Module() {
        return new JsonNullableJackson3Module();
    }
}
