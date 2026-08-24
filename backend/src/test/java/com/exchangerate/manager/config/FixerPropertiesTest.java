package com.exchangerate.manager.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FixerPropertiesTest {

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

    @Test
    void acceptsValidHttpPolicy() {
        assertThat(validate(Duration.ofSeconds(3), Duration.ofSeconds(10), 3,
                Duration.ofMillis(500), 2.0, Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        assertThat(validate(Duration.ZERO, Duration.ofSeconds(10), 3,
                Duration.ofMillis(500), 2.0, Duration.ofSeconds(1))).isNotEmpty();
        assertThat(validate(Duration.ofSeconds(3), Duration.ofSeconds(-1), 3,
                Duration.ofMillis(500), 2.0, Duration.ofSeconds(1))).isNotEmpty();
    }

    @Test
    void rejectsInvalidAttemptCountMultiplierAndDelays() {
        assertThat(validate(Duration.ofSeconds(3), Duration.ofSeconds(10), 0,
                Duration.ofMillis(500), 2.0, Duration.ofSeconds(1))).isNotEmpty();
        assertThat(validate(Duration.ofSeconds(3), Duration.ofSeconds(10), 3,
                Duration.ofMillis(500), 0.5, Duration.ofSeconds(1))).isNotEmpty();
        assertThat(validate(Duration.ofSeconds(3), Duration.ofSeconds(10), 3,
                Duration.ZERO, 2.0, Duration.ofSeconds(1))).isNotEmpty();
        assertThat(validate(Duration.ofSeconds(3), Duration.ofSeconds(10), 3,
                Duration.ofSeconds(2), 2.0, Duration.ofSeconds(1))).isNotEmpty();
    }

    private static Set<ConstraintViolation<FixerProperties>> validate(
            Duration connectTimeout, Duration readTimeout, int maxAttempts,
            Duration initialDelay, double multiplier, Duration maxDelay) {
        FixerProperties properties = new FixerProperties("key", "https://fixer.test",
                new FixerProperties.Http(connectTimeout, readTimeout,
                        new FixerProperties.Retry(maxAttempts, initialDelay, multiplier, maxDelay)));
        return validator.validate(properties);
    }
}
