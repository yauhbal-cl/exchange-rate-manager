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
import java.util.List;
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

    @Test
    void findTrendIncludesDatePresentForBothCurrencies() {
        String from = "AUD";
        String to = "CAD";
        LocalDate rateDate = LocalDate.of(2026, 8, 10);
        BigDecimal fromRate = new BigDecimal("0.660000");
        BigDecimal toRate = new BigDecimal("0.730000");

        repository.upsert(from, fromRate, rateDate);
        repository.upsert(to, toRate, rateDate);
        entityManager.clear();

        List<ExchangeRateRepository.RateTrendProjection> trend =
                repository.findTrend(from, to, rateDate, rateDate);

        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).getRateDate()).isEqualTo(rateDate);
        assertThat(trend.get(0).getFromRateToUsd()).isEqualByComparingTo(fromRate);
        assertThat(trend.get(0).getToRateToUsd()).isEqualByComparingTo(toRate);
    }

    @Test
    void findTrendExcludesDatePresentForOnlyOneCurrency() {
        String from = "CHF";
        String to = "NZD";
        LocalDate rateDate = LocalDate.of(2026, 8, 10);

        repository.upsert(from, new BigDecimal("1.100000"), rateDate);
        // no rate stored for `to` on this date
        entityManager.clear();

        List<ExchangeRateRepository.RateTrendProjection> trend =
                repository.findTrend(from, to, rateDate, rateDate);

        assertThat(trend).isEmpty();
    }

    @Test
    void findTrendIncludesBoundaryDatesAndExcludesDatesOutsideRange() {
        String from = "SEK";
        String to = "NOK";
        LocalDate startDate = LocalDate.of(2026, 8, 10);
        LocalDate endDate = LocalDate.of(2026, 8, 12);
        LocalDate beforeStart = startDate.minusDays(1);
        LocalDate afterEnd = endDate.plusDays(1);

        for (LocalDate date : List.of(beforeStart, startDate, endDate, afterEnd)) {
            repository.upsert(from, new BigDecimal("0.100000"), date);
            repository.upsert(to, new BigDecimal("0.200000"), date);
        }
        entityManager.clear();

        List<ExchangeRateRepository.RateTrendProjection> trend =
                repository.findTrend(from, to, startDate, endDate);

        assertThat(trend)
                .extracting(ExchangeRateRepository.RateTrendProjection::getRateDate)
                .containsExactly(startDate, endDate);
    }

    @Test
    void findTrendReturnsResultsOrderedChronologicallyAscending() {
        String from = "DKK";
        String to = "PLN";
        LocalDate earliest = LocalDate.of(2026, 8, 10);
        LocalDate middle = LocalDate.of(2026, 8, 11);
        LocalDate latest = LocalDate.of(2026, 8, 12);

        // Insert out of order to verify the query enforces ordering, not insertion order.
        for (LocalDate date : List.of(latest, earliest, middle)) {
            repository.upsert(from, new BigDecimal("0.300000"), date);
            repository.upsert(to, new BigDecimal("0.400000"), date);
        }
        entityManager.clear();

        List<ExchangeRateRepository.RateTrendProjection> trend =
                repository.findTrend(from, to, earliest, latest);

        assertThat(trend)
                .extracting(ExchangeRateRepository.RateTrendProjection::getRateDate)
                .containsExactly(earliest, middle, latest);
    }

    @Test
    void findTrendReturnsEmptyListWhenNoQualifyingDatesInRange() {
        String from = "HUF";
        String to = "CZK";
        LocalDate startDate = LocalDate.of(2030, 1, 1);
        LocalDate endDate = LocalDate.of(2030, 1, 31);

        List<ExchangeRateRepository.RateTrendProjection> trend =
                repository.findTrend(from, to, startDate, endDate);

        assertThat(trend).isEmpty();
    }
}
