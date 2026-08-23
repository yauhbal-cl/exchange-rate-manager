package com.exchangerate.manager.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fixed, externalized spread policy configuration, bound from the {@code exchange-rates} prefix
 * (see {@code application.yml}).
 *
 * <p>{@link #baseCurrency()} is the provider's (Fixer.io's) business base currency, used for
 * spread policy — the currency for which spread-based conversion charges 0% spread. This is a
 * deliberately separate concept from the {@code USD} internal-normalization
 * anchor used elsewhere in this codebase (e.g. {@code rate_to_usd} on the rate entity): that
 * {@code USD} literal is only a fixed unit that stored rates are additionally normalized against,
 * and has no bearing on which currency is spread-free. Renaming or otherwise touching that
 * unrelated concept is out of scope here — this class exists solely to stop conflating the two.
 *
 * <p>Constructor-bound and immutable, per the project's config-as-data conventions: adding or
 * changing a spread is an {@code application.yml} change, not a code change.
 */
@ConfigurationProperties(prefix = "exchange-rates")
@Validated
public record ExchangeRateProperties(

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String baseCurrency,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax(value = "100.0", inclusive = false)
        BigDecimal defaultSpreadPercent,

        @NotEmpty
        Map<String, BigDecimal> spreads) {

    private static final java.util.regex.Pattern CURRENCY_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z]{3}$");

    /**
     * Validates every entry of {@link #spreads()} individually, since {@code jakarta.validation}
     * does not natively validate {@code Map} keys/values via simple field annotations: each key
     * must match {@code ^[A-Z]{3}$} and each value must be in {@code [0, 100)}.
     */
    @AssertTrue(message = "spreads must have keys matching ^[A-Z]{3}$ and values in [0, 100)")
    public boolean isSpreadsValid() {
        if (spreads == null) {
            return false;
        }
        for (Map.Entry<String, BigDecimal> entry : spreads.entrySet()) {
            String key = entry.getKey();
            BigDecimal value = entry.getValue();
            if (key == null || !CURRENCY_CODE_PATTERN.matcher(key).matches()) {
                return false;
            }
            if (value == null
                    || value.compareTo(BigDecimal.ZERO) < 0
                    || value.compareTo(new BigDecimal("100.0")) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Asserts that {@link #baseCurrency()} has an explicit, exact 0% entry in {@link #spreads()} —
     * the provider's own currency must never carry a spread.
     */
    @AssertTrue(message = "spreads must contain an explicit 0 entry for baseCurrency")
    public boolean isBaseCurrencySpreadZero() {
        if (spreads == null || baseCurrency == null) {
            return false;
        }
        BigDecimal baseSpread = spreads.get(baseCurrency);
        return baseSpread != null && baseSpread.compareTo(BigDecimal.ZERO) == 0;
    }
}
