package com.exchangerate.manager.repository;

import com.exchangerate.manager.entity.ExchangeRate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer test against the real docker-compose PostgreSQL instance
 * (see application.yml). Not H2/Testcontainers, per research.md: the
 * correctness being verified here (unique constraints, CHECK constraints,
 * NUMERIC precision) is PostgreSQL-specific.
 *
 * The class is wrapped in a transaction that is rolled back after each test
 * so saved rows never leak between test runs.
 */
@SpringBootTest
@Transactional
class ExchangeRateRepositoryTest {

    private static final String CURRENCY_CODE = "EUR";
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 8, 20);
    private static final BigDecimal RATE_TO_USD = new BigDecimal("0.920000");

    @Autowired
    private ExchangeRateRepository repository;

    @Test
    void savesAndFindsByCurrencyCodeAndRateDate() {
        ExchangeRate rate = new ExchangeRate();
        rate.setCurrencyCode(CURRENCY_CODE);
        rate.setRateToUsd(RATE_TO_USD);
        rate.setRateDate(RATE_DATE);

        repository.save(rate);

        Optional<ExchangeRate> found = repository.findByCurrencyCodeAndRateDate(CURRENCY_CODE, RATE_DATE);

        assertThat(found).isPresent();
        assertThat(found.get().getCurrencyCode()).isEqualTo(CURRENCY_CODE);
        assertThat(found.get().getRateDate()).isEqualTo(RATE_DATE);
        assertThat(found.get().getRateToUsd()).isEqualByComparingTo(RATE_TO_USD);
    }

    @Test
    void returnsEmptyWhenNoRateExistsForNaturalKey() {
        Optional<ExchangeRate> found = repository.findByCurrencyCodeAndRateDate("ZZZ", LocalDate.of(1999, 1, 1));

        assertThat(found).isEmpty();
    }
}
