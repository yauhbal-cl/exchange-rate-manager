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
                        "HKD", new BigDecimal("3.25"),
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

    @Test
    void hkdResolvesToThreePointTwoFivePercentGroup() {
        BigDecimal spread = spreadLookup().spreadFor("HKD");

        assertThat(spread).isEqualByComparingTo(new BigDecimal("3.25"));
    }

    @Test
    void myrResolvesToFourPointFiveZeroPercentGroup() {
        BigDecimal spread = spreadLookup().spreadFor("MYR");

        assertThat(spread).isEqualByComparingTo(new BigDecimal("4.50"));
    }

    @Test
    void rubResolvesToSixPercentGroup() {
        BigDecimal spread = spreadLookup().spreadFor("RUB");

        assertThat(spread).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    @Test
    void gbpNotInAnyGroupAndNotEurFallsBackToConfiguredDefault() {
        BigDecimal spread = spreadLookup().spreadFor("GBP");

        assertThat(spread).isEqualByComparingTo(DEFAULT_SPREAD_PERCENT);
    }

    /**
     * SC-004: a new currency added to a spread group, and a changed default percentage, must be
     * reflected purely by editing {@link ExchangeRateProperties} — no change to
     * {@link SpreadLookup}'s calculation logic. This test builds a second, independent properties
     * instance (distinct spreads map, distinct default) that neither appears in nor overlaps with
     * {@link #spreadLookup()}, and proves {@link SpreadLookup#spreadFor(String)} picks up both the
     * newly added currency and the changed default without any production code change.
     */
    @Test
    void newCurrencyAndChangedDefaultAreHonoredThroughConfigAloneWithNoLogicChange() {
        BigDecimal changedDefaultSpreadPercent = new BigDecimal("3.00");
        BigDecimal newCurrencySpreadPercent = new BigDecimal("5.00");
        ExchangeRateProperties reconfiguredProperties = new ExchangeRateProperties(
                "EUR",
                changedDefaultSpreadPercent,
                Map.of(
                        "EUR", new BigDecimal("0.00"),
                        "SEK", newCurrencySpreadPercent));
        SpreadLookup reconfiguredSpreadLookup = new SpreadLookup(reconfiguredProperties);

        BigDecimal sekSpread = reconfiguredSpreadLookup.spreadFor("SEK");
        BigDecimal fallbackSpread = reconfiguredSpreadLookup.spreadFor("GBP");

        assertThat(sekSpread).isEqualByComparingTo(newCurrencySpreadPercent);
        assertThat(fallbackSpread).isEqualByComparingTo(changedDefaultSpreadPercent);
        assertThat(fallbackSpread).isNotEqualByComparingTo(DEFAULT_SPREAD_PERCENT);
    }
}
