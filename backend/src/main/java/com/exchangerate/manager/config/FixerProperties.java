package com.exchangerate.manager.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Configuration for the Fixer HTTP client and its isolated retry policy. */
@ConfigurationProperties(prefix = "fixer")
@Validated
public record FixerProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl,
        @NotNull @Valid Http http) {

    public record Http(
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout,
            @NotNull @Valid Retry retry) {

        @AssertTrue(message = "connect-timeout must be positive")
        public boolean isConnectTimeoutPositive() {
            return connectTimeout != null && !connectTimeout.isZero() && !connectTimeout.isNegative();
        }

        @AssertTrue(message = "read-timeout must be positive")
        public boolean isReadTimeoutPositive() {
            return readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative();
        }
    }

    public record Retry(
            @Min(1) int maxAttempts,
            @NotNull Duration initialDelay,
            @DecimalMin("1.0") double multiplier,
            @NotNull Duration maxDelay) {

        @AssertTrue(message = "initial-delay must be positive")
        public boolean isInitialDelayPositive() {
            return initialDelay != null && !initialDelay.isZero() && !initialDelay.isNegative();
        }

        @AssertTrue(message = "max-delay must be positive")
        public boolean isMaxDelayPositive() {
            return maxDelay != null && !maxDelay.isZero() && !maxDelay.isNegative();
        }

        @AssertTrue(message = "max-delay must not be less than initial-delay")
        public boolean isDelayRangeValid() {
            return initialDelay != null && maxDelay != null && maxDelay.compareTo(initialDelay) >= 0;
        }
    }
}
