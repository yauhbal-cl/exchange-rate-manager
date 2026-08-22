package com.exchangerate.manager.repository;

import com.exchangerate.manager.AbstractIntegrationTest;
import com.exchangerate.manager.entity.ExchangeRate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer test against a Testcontainers-managed PostgreSQL instance (see
 * {@link AbstractIntegrationTest}): the correctness being verified here (unique constraints,
 * CHECK constraints, NUMERIC precision) is PostgreSQL-specific.
 *
 * The class is wrapped in a transaction that is rolled back after each test
 * so saved rows never leak between test runs.
 */
@SpringBootTest
@Transactional
class ExchangeRateRepositoryTest extends AbstractIntegrationTest {

    private static final String CURRENCY_CODE = "EUR";
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 8, 20);
    private static final BigDecimal RATE_TO_USD = new BigDecimal("0.920000");

    @Autowired
    private ExchangeRateRepository repository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void upsertInsertsWhenAbsent() {
        String currencyCode = "JPY";
        LocalDate rateDate = LocalDate.of(2026, 8, 21);
        BigDecimal rateToUsd = new BigDecimal("0.006700");

        repository.upsert(currencyCode, rateToUsd, rateDate);
        entityManager.clear();

        Optional<ExchangeRate> found = repository.findByCurrencyCodeAndRateDate(currencyCode, rateDate);

        assertThat(found).isPresent();
        assertThat(found.get().getCurrencyCode()).isEqualTo(currencyCode);
        assertThat(found.get().getRateDate()).isEqualTo(rateDate);
        assertThat(found.get().getRateToUsd()).isEqualByComparingTo(rateToUsd);
    }

    @Test
    void upsertUpdatesInPlaceOnConflict() {
        String currencyCode = "GBP";
        LocalDate rateDate = LocalDate.of(2026, 8, 21);
        BigDecimal initialRate = new BigDecimal("1.270000");
        BigDecimal updatedRate = new BigDecimal("1.310000");

        repository.upsert(currencyCode, initialRate, rateDate);
        entityManager.clear();

        Optional<ExchangeRate> afterInsert = repository.findByCurrencyCodeAndRateDate(currencyCode, rateDate);
        assertThat(afterInsert).isPresent();
        assertThat(afterInsert.get().getRateToUsd()).isEqualByComparingTo(initialRate);

        repository.upsert(currencyCode, updatedRate, rateDate);
        entityManager.clear();

        Optional<ExchangeRate> afterUpdate = repository.findByCurrencyCodeAndRateDate(currencyCode, rateDate);
        assertThat(afterUpdate).isPresent();
        assertThat(afterUpdate.get().getCurrencyCode()).isEqualTo(currencyCode);
        assertThat(afterUpdate.get().getRateDate()).isEqualTo(rateDate);
        assertThat(afterUpdate.get().getRateToUsd()).isEqualByComparingTo(updatedRate);
    }
}
