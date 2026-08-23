package com.exchangerate.manager.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the Bean Validation constraints declared on {@link ExchangeRateProperties}: the
 * field-level annotations plus the two instance {@code @AssertTrue} methods. Exercises a plain
 * {@link Validator} directly against record instances built by hand — no Spring context, since
 * this is testing jakarta.validation constraint evaluation, not Spring property binding.
 */
class ExchangeRatePropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    private static Map<String, BigDecimal> validSpreads() {
        Map<String, BigDecimal> spreads = new HashMap<>();
        spreads.put("EUR", BigDecimal.ZERO);
        spreads.put("GBP", new BigDecimal("3.25"));
        spreads.put("JPY", new BigDecimal("3.25"));
        return spreads;
    }

    @Test
    void validPropertiesHaveNoViolations() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("2.75"), validSpreads());

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void blankBaseCurrencyIsRejected() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "", new BigDecimal("2.75"), validSpreads());

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void lowercaseBaseCurrencyIsRejected() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "eur", new BigDecimal("2.75"), validSpreads());

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void fourLetterBaseCurrencyIsRejected() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EURO", new BigDecimal("2.75"), validSpreads());

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void negativeDefaultSpreadPercentIsRejected() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("-1"), validSpreads());

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void defaultSpreadPercentAtOrAboveOneHundredIsRejected() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("100.0"), validSpreads());

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void spreadValueOutOfRangeIsRejected() {
        Map<String, BigDecimal> spreads = validSpreads();
        spreads.put("CHF", new BigDecimal("-5"));

        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("2.75"), spreads);

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void spreadValueAtOrAboveOneHundredIsRejected() {
        Map<String, BigDecimal> spreads = validSpreads();
        spreads.put("CHF", new BigDecimal("100.0"));

        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("2.75"), spreads);

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void emptySpreadsMapIsRejected() {
        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("2.75"), Map.of());

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void spreadsMissingBaseCurrencyEntryIsRejected() {
        Map<String, BigDecimal> spreads = new HashMap<>();
        spreads.put("GBP", new BigDecimal("3.25"));
        spreads.put("JPY", new BigDecimal("3.25"));

        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("2.75"), spreads);

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void spreadsWithNonZeroBaseCurrencyEntryIsRejected() {
        Map<String, BigDecimal> spreads = validSpreads();
        spreads.put("EUR", new BigDecimal("1.00"));

        ExchangeRateProperties properties = new ExchangeRateProperties(
                "EUR", new BigDecimal("2.75"), spreads);

        Set<ConstraintViolation<ExchangeRateProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }
}
