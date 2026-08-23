package com.exchangerate.manager.service;

import com.exchangerate.manager.config.ExchangeRateProperties;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link SpreadLookup}. Plain JUnit 5, no Spring context and no mocking — a
 * hand-built {@link ExchangeRateProperties} record is enough since it's an immutable plain record.
 *
 * <p>Covers the T007 bug-fix mechanism: currencies absent from the configured {@code spreads} map
 * must fall back to {@code defaultSpreadPercent}, not silently resolve to 0% (the prior bug, where
 * USD was implicitly spread-free). Full Appendix B group coverage is added later (T009/T010); the
 * fixture below is intentionally reusable so more {@code @Test} methods can be appended without
 * touching setup.
 */
class SpreadLookupTest {

    private static final BigDecimal DEFAULT_SPREAD_PERCENT = new BigDecimal("2.75");

    private static SpreadLookup spreadLookup() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR",
                DEFAULT_SPREAD_PERCENT,
                Map.of(
                        "EUR", new BigDecimal("0.00"),
                        "JPY", new BigDecimal("3.25"),
                        "MYR", new BigDecimal("4.50"),
                        "RUB", new BigDecimal("6.00")));
        return new SpreadLookup(properties);
    }

    @Test
    void currencyPresentInSpreadsWithZeroValueResolvesToZero() {
        BigDecimal spread = spreadLookup().spreadFor("EUR");

        assertThat(spread).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void currencyAbsentFromSpreadsResolvesToConfiguredDefaultNotZero() {
        BigDecimal spread = spreadLookup().spreadFor("USD");

        assertThat(spread).isEqualByComparingTo(DEFAULT_SPREAD_PERCENT);
        assertThat(spread).isNotEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void currencyPresentInSpreadsResolvesToItsConfiguredValue() {
        BigDecimal spread = spreadLookup().spreadFor("JPY");

        assertThat(spread).isEqualByComparingTo(new BigDecimal("3.25"));
    }
}
